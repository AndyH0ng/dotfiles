#!/usr/bin/env python3
"""Local ACP v1 adapter for the installed Claude Code CLI. No credentials stored."""
import asyncio
import json
import os
import re
import shutil
import signal
import sys
import time
import uuid
from datetime import datetime
from pathlib import Path


class Bridge:
    @staticmethod
    def model_label(model):
        name = re.sub(r"\s*\(recommend(?:ed)?\)", "", model["displayName"], flags=re.IGNORECASE).strip()
        resolved = re.sub(r"\[.*?\]", "", model.get("resolvedModel", ""))
        match = re.search(r"(?<!\d)(\d{1,3}(?:[-.]\d{1,3})*)(?!\d)", resolved)
        if not match:
            return name
        version = match[1].replace("-", ".")
        if name.casefold() == "default":
            family = " ".join(part.capitalize() for part in resolved.removeprefix("claude-").split("-")
                              if part.isalpha())
            return f"Default ({family} {version})" if family else f"Default ({version})"
        return name if version in name else f"{name} {version}"

    def air_models(self):
        """Show Claude's default alias as the resolved model, without a duplicate row."""
        default = next((m for m in self.models if m["value"] == "default"), None)
        resolved = default.get("resolvedModel") if default else None
        matching = next((m for m in self.models
                         if m is not default and m.get("resolvedModel") == resolved), None) if resolved else None
        for model in self.models:
            if model is matching:
                continue
            label = self.model_label(matching) if model is default and matching else self.model_label(model)
            yield model, label

    def air_model_value(self, value):
        visible = {model["value"] for model, _ in self.air_models()}
        if value in visible:
            return value
        selected = next((m for m in self.models if m["value"] == value), None)
        default = next((m for m in self.models if m["value"] == "default"), None)
        if selected and default and selected.get("resolvedModel") == default.get("resolvedModel"):
            return "default"
        return self.models[0]["value"]

    def __init__(self):
        self.sessions = {}
        self.pending = {}
        self.tasks = set()
        self.request_number = 0
        self.models = []

    @staticmethod
    def state_path(sid):
        return Path(__file__).resolve().with_name("sessions") / f"{sid}.json"

    def save_session(self, sid):
        session = self.sessions[sid]
        target = self.state_path(sid)
        target.parent.mkdir(mode=0o700, exist_ok=True)
        data = {key: session[key] for key in ("cwd", "model", "effort", "mode", "started")
                if key in session}
        staged = target.with_name(f".{sid}.{os.getpid()}.tmp")
        staged.write_text(json.dumps(data) + "\n")
        staged.chmod(0o600)
        staged.replace(target)

    @staticmethod
    def transcript_exists(sid):
        projects = Path.home() / ".claude/projects"
        return any(projects.glob(f"*/{sid}.jsonl"))

    @staticmethod
    def session_cwd(params):
        cwd = Path(params["cwd"])
        if not cwd.is_absolute() or not cwd.is_dir():
            raise ValueError("Session cwd must be an existing absolute directory")
        return cwd

    @staticmethod
    def session_mcp(params):
        air_mcp = Path.home() / "Library/Application Support/JetBrains/Air/mcp.json"
        mcp = json.loads(air_mcp.read_text()).get("mcpServers", {}) if air_mcp.exists() else {}
        mcp = {name: config for name, config in mcp.items() if not config.get("disabled", False)}
        for server in params.get("mcpServers", []):
            if server.get("type") in ("http", "sse"):
                mcp[server["name"]] = {"type": server["type"], "url": server["url"],
                    "headers": {item["name"]: item["value"] for item in server.get("headers", [])}}
            else:
                mcp[server["name"]] = {"command": server["command"], "args": server.get("args", []),
                    "env": {item["name"]: item["value"] for item in server.get("env", [])}}
        return mcp

    def session_result(self, sid):
        session = self.sessions[sid]
        configs = self.config_options(session)
        return {"sessionId": sid, "configOptions": configs,
            "models": {"currentModelId": self.air_model_value(session["model"]), "availableModels": [
                {"modelId": m["value"], "name": label, "description": m.get("description", "")}
                for m, label in self.air_models()]},
            "modes": {"currentModeId": session["mode"], "availableModes": [
                {"id": o["value"], "name": o["name"], "description": o["description"]}
                for o in configs[1]["options"]]}}

    async def discover_models(self):
        if self.models:
            return
        proc = await asyncio.create_subprocess_exec(
            os.environ.get("AIR_CLAUDE_BIN", "/opt/homebrew/bin/claude"),
            "-p", "--input-format", "stream-json", "--output-format", "stream-json",
            "--verbose", "--strict-mcp-config", "--mcp-config", '{"mcpServers":{}}',
            stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.DEVNULL, cwd=str(Path.home()), limit=8*1024*1024)
        try:
            proc.stdin.write((json.dumps({"type": "control_request", "request_id": "models",
                "request": {"subtype": "initialize", "hooks": {}}}) + "\n").encode())
            await proc.stdin.drain()
            async def read():
                while line := await proc.stdout.readline():
                    event = json.loads(line)
                    if event.get("type") == "control_response":
                        self.models = event.get("response", {}).get("response", {}).get("models", [])
                        return
            await asyncio.wait_for(read(), 30)
            if not self.models:
                raise RuntimeError("Claude Code did not return available models")
        finally:
            proc.stdin.close()
            if proc.returncode is None:
                proc.terminate()
            try:
                await asyncio.wait_for(proc.wait(), 5)
            except TimeoutError:
                proc.kill()
                await proc.wait()

    def config_options(self, session):
        model = next(m for m in self.models if m["value"] == session["model"])
        options = [
            {"id": "model", "name": "Model", "category": "model", "type": "select",
             "currentValue": self.air_model_value(session["model"]), "options": [
                 {"value": m["value"], "name": label, "description": m.get("description", "")}
                 for m, label in self.air_models()]},
            {"id": "mode", "name": "Mode", "category": "mode", "type": "select",
             "currentValue": session["mode"], "options": [
                 {"value": "manual", "name": "Ask", "description": "Request permission for changes"},
                 {"value": "plan", "name": "Plan", "description": "Explore and plan before implementation"},
                 {"value": "acceptEdits", "name": "Accept edits", "description": "Allow file edits; other permissions still apply"}]}]
        if model.get("supportsEffort"):
            levels = model.get("supportedEffortLevels", [])
            options.append({"id": "effort", "name": "Reasoning effort", "category": "thought_level",
                "type": "select", "currentValue": session["effort"],
                "options": [{"value": "default", "name": "Default"}] + [
                    {"value": level, "name": level.capitalize()} for level in levels]})
        return options

    def set_option(self, params):
        session = self.sessions[params["sessionId"]]
        key, value = params["configId"], params["value"]
        option = next((o for o in self.config_options(session) if o["id"] == key), None)
        if not option or value not in [o["value"] for o in option["options"]]:
            raise ValueError("Unsupported configuration value")
        session[key] = value
        if key == "model":
            session["effort"] = "default"
        self.save_session(params["sessionId"])
        result = self.config_options(session)
        self.update(params["sessionId"], {"sessionUpdate": "config_option_update", "configOptions": result})
        if key == "mode":
            self.update(params["sessionId"], {"sessionUpdate": "current_mode_update", "currentModeId": value})
        return {"configOptions": result}

    def send(self, message):
        print(json.dumps({"jsonrpc": "2.0", **message}, ensure_ascii=False), flush=True)

    def update(self, sid, update):
        self.send({"method": "session/update", "params": {"sessionId": sid, "update": update}})

    def report_usage(self, sid, result):
        session = self.sessions[sid]
        usage = session.get("last_usage")
        models = result.get("modelUsage", {})
        model = models.get(session.get("active_model"), {})
        if not model and len(models) == 1:
            model = next(iter(models.values()))
        size = model.get("contextWindow")
        if not usage or not isinstance(size, int) or size <= 0:
            return
        # Result usage aggregates every API call in the turn. The final
        # assistant message reports the current context, including cache.
        used = sum(usage.get(key, 0) or 0 for key in (
            "input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens", "output_tokens"))
        if not isinstance(used, int) or used < 0:
            return
        self.update(sid, {"sessionUpdate": "usage_update", "used": used, "size": size})

    def record_limits(self, sid, event):
        info = event.get("rate_limit_info") or event.get("rateLimitInfo") or {}
        windows = dict(info.get("unifiedWindows") or {})
        if info.get("rateLimitType") in ("five_hour", "seven_day"):
            windows.setdefault(info["rateLimitType"], info)
        limits = self.sessions[sid].setdefault("limits", {})
        for key in ("five_hour", "seven_day"):
            window = windows.get(key, {})
            value, reset = window.get("utilization"), window.get("resetsAt")
            if isinstance(value, (int, float)) and not isinstance(value, bool) and 0 <= value <= 1:
                limits[key] = {"used": value * 100, "reset": reset}
        if limits:
            target = Path(__file__).resolve().with_name("usage.properties")
            values = [f"updated_at={time.time()}"]
            for key, window in limits.items():
                values.append(f"{key}.used={window['used']}")
                if isinstance(window.get("reset"), (int, float)):
                    values.append(f"{key}.reset={window['reset']}")
            # Only account utilization and reset times; never tokens or keys.
            staged = target.with_name(f"usage.{sid}.tmp")
            try:
                staged.write_text("\n".join(values) + "\n")
                staged.chmod(0o600)
                staged.replace(target)
            except OSError as exc:
                print(f"Cannot publish UI usage data: {exc}", file=sys.stderr)

    def limit_text(self, sid):
        parts = []
        for key, label in (("five_hour", "5시간"), ("seven_day", "주간")):
            window = self.sessions[sid].get("limits", {}).get(key)
            if not window:
                continue
            reset = window.get("reset")
            if isinstance(reset, (int, float)) and reset <= time.time():
                continue
            text = f"{label} {window['used']:.0f}%"
            if isinstance(reset, (int, float)):
                text += f" (초기화 {datetime.fromtimestamp(reset).astimezone():%m/%d %H:%M})"
            parts.append(text)
        return " · ".join(parts)

    async def request(self, method, params):
        self.request_number += 1
        ident = f"bridge-{self.request_number}"
        future = asyncio.get_running_loop().create_future()
        self.pending[ident] = future
        self.send({"id": ident, "method": method, "params": params})
        try:
            return await asyncio.wait_for(future, 600)
        finally:
            self.pending.pop(ident, None)

    async def permission(self, sid, message):
        req = message["request"]
        if req.get("subtype") != "can_use_tool":
            return {"behavior": "deny", "message": "Unsupported control request"}
        tid = req.get("tool_use_id") or message["request_id"]
        tool = {"toolCallId": tid, "title": req["tool_name"], "status": "pending",
                "kind": "other", "rawInput": req["input"]}
        try:
            reply = await self.request("session/request_permission", {
                "sessionId": sid, "toolCall": tool,
                "options": [{"optionId": "allow", "name": "Allow once", "kind": "allow_once"},
                            {"optionId": "deny", "name": "Deny", "kind": "reject_once"}]})
            outcome = reply.get("outcome", {})
            if outcome.get("outcome") == "selected" and outcome.get("optionId") == "allow":
                return {"behavior": "allow", "updatedInput": req["input"]}
        except (RuntimeError, TimeoutError) as exc:
            print(f"Permission request failed: {exc}", file=sys.stderr)
        return {"behavior": "deny", "message": "Permission not granted in Air"}

    async def prompt(self, params):
        sid = params["sessionId"]
        session = self.sessions[sid]
        if session.get("process"):
            raise ValueError("A prompt is already running in this session")
        content = []
        for block in params["prompt"]:
            if block.get("type") == "text":
                content.append(block["text"])
            elif block.get("type") == "resource" and "text" in block.get("resource", {}):
                content.append(block["resource"]["text"])
            else:
                raise ValueError("This bridge currently supports text and embedded text only")
        command = [os.environ.get("AIR_CLAUDE_BIN") or shutil.which("claude") or "/opt/homebrew/bin/claude",
                   "--print", "--verbose", "--input-format", "stream-json",
                   "--output-format", "stream-json", "--include-partial-messages",
                   "--permission-mode", session["mode"], "--permission-prompts", "host",
                   "--permission-prompt-tool", "stdio"]
        if session["model"] != "default":
            command += ["--model", session["model"]]
        if session["effort"] != "default":
            command += ["--effort", session["effort"]]
        if session.get("started"):
            command += ["--resume", sid]
        else:
            command += ["--session-id", sid]
        # Air owns MCP configuration in this integration. Never auto-load the
        # CLI's global/project MCPs as well: names and transports can differ.
        command += ["--strict-mcp-config", "--mcp-config", json.dumps({"mcpServers": session["mcp"]})]
        env = dict(os.environ)
        # Run the actual CLI, retaining its user configuration and login.
        # No SDK identity headers, OAuth extraction, or permission bypass.
        if env.get("ANTHROPIC_API_KEY"):
            raise ValueError("ANTHROPIC_API_KEY is set; remove it to use the Claude subscription")
        proc = await asyncio.create_subprocess_exec(
            *command, cwd=session["cwd"], env=env,
            stdin=asyncio.subprocess.PIPE, stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE, start_new_session=True, limit=8 * 1024 * 1024)
        session["process"] = proc
        session["cancelled"] = False
        stderr_task = asyncio.create_task(proc.stderr.read())
        sent_text = False
        result = None
        async def write(data):
            proc.stdin.write((json.dumps(data) + "\n").encode())
            await proc.stdin.drain()
        try:
            await write({"type": "control_request", "request_id": "init",
                         "request": {"subtype": "initialize", "hooks": {}}})
            await write({"type": "user", "message": {"role": "user", "content": "\n\n".join(content)}})
            while line := await proc.stdout.readline():
                event = json.loads(line)
                kind = event.get("type")
                if kind == "control_request":
                    decision = await self.permission(sid, event)
                    await write({"type": "control_response", "response": {
                        "subtype": "success", "request_id": event["request_id"], "response": decision}})
                elif kind == "system" and event.get("subtype") == "init":
                    session["started"] = True
                    session["active_model"] = event.get("model")
                    self.save_session(sid)
                elif kind == "stream_event":
                    delta = event.get("event", {}).get("delta", {})
                    if delta.get("type") == "text_delta":
                        sent_text = True
                        self.update(sid, {"sessionUpdate": "agent_message_chunk",
                                          "content": {"type": "text", "text": delta["text"]}})
                elif kind == "assistant":
                    message_usage = event.get("message", {}).get("usage")
                    if message_usage:
                        session["last_usage"] = message_usage
                    for block in event.get("message", {}).get("content", []):
                        if block.get("type") == "tool_use":
                            self.update(sid, {"sessionUpdate": "tool_call", "toolCallId": block["id"],
                                              "title": block["name"], "kind": "other", "status": "in_progress",
                                              "rawInput": block.get("input", {})})
                elif kind == "user":
                    for block in event.get("message", {}).get("content", []):
                        if block.get("type") == "tool_result":
                            self.update(sid, {"sessionUpdate": "tool_call_update",
                                              "toolCallId": block["tool_use_id"],
                                              "status": "failed" if block.get("is_error") else "completed",
                                              "rawOutput": block.get("content")})
                elif kind == "rate_limit_event":
                    self.record_limits(sid, event)
                elif kind == "result":
                    result = event
                    self.report_usage(sid, result)
                    break
            if session["cancelled"]:
                return {"stopReason": "cancelled"}
            if result is None:
                raise RuntimeError("Claude Code ended without a result")
            if result.get("is_error"):
                raise RuntimeError(result.get("result") or "; ".join(result.get("errors", [])) or "Claude Code failed")
            if not sent_text and result.get("result"):
                self.update(sid, {"sessionUpdate": "agent_message_chunk", "content": {
                    "type": "text", "text": result["result"]}})
            return {"stopReason": "end_turn"}
        finally:
            if proc.stdin and not proc.stdin.is_closing():
                proc.stdin.close()
            try:
                await asyncio.wait_for(proc.wait(), 5)
            except TimeoutError:
                os.killpg(proc.pid, signal.SIGTERM)
                await proc.wait()
            stderr = await stderr_task
            if not result and stderr:
                print(stderr.decode(errors="replace")[-2000:], file=sys.stderr)
            session["process"] = None

    async def dispatch(self, message):
        ident = message.get("id")
        method = message["method"]
        params = message.get("params", {})
        try:
            if method == "initialize":
                result = {"protocolVersion": 1, "agentCapabilities": {
                    "promptCapabilities": {"embeddedContext": True},
                    "mcpCapabilities": {"http": True, "sse": True},
                    "sessionCapabilities": {"resume": {}}}, "authMethods": [],
                    "agentInfo": {"name": "claude-code", "title": "Claude Code", "version": "0.4.0"}}
            elif method == "session/new":
                await self.discover_models()
                cwd = self.session_cwd(params)
                mcp = self.session_mcp(params)
                sid = str(uuid.uuid4())
                self.sessions[sid] = {"cwd": str(cwd), "mcp": mcp,
                    "model": self.models[0]["value"], "effort": "default", "mode": "manual",
                    "started": False}
                self.save_session(sid)
                result = self.session_result(sid)
            elif method == "session/resume":
                await self.discover_models()
                sid = str(uuid.UUID(params["sessionId"]))
                cwd = self.session_cwd(params)
                stored = {}
                path = self.state_path(sid)
                if path.exists():
                    stored = json.loads(path.read_text())
                    if Path(stored["cwd"]).resolve() != cwd.resolve():
                        raise ValueError("Session cwd does not match the original workspace")
                started = self.transcript_exists(sid)
                if not stored and not started:
                    raise ValueError("Claude Code session does not exist locally")
                if stored.get("started") and not started:
                    raise ValueError("Claude Code session transcript is missing")
                model = stored.get("model", self.models[0]["value"])
                model = self.air_model_value(model)
                mode = stored.get("mode", "manual")
                if mode not in ("manual", "plan", "acceptEdits"):
                    mode = "manual"
                self.sessions[sid] = {"cwd": str(cwd), "mcp": self.session_mcp(params),
                    "model": model, "effort": stored.get("effort", "default"),
                    "mode": mode, "started": started}
                self.save_session(sid)
                result = self.session_result(sid)
            elif method == "session/set_config_option":
                result = self.set_option(params)
            elif method in ("session/set_mode", "session/set_model"):
                key = "mode" if method.endswith("set_mode") else "model"
                self.set_option({"sessionId": params["sessionId"], "configId": key,
                    "value": params["modeId" if key == "mode" else "modelId"]})
                result = {}
            elif method == "session/prompt":
                result = await self.prompt(params)
            elif method == "session/cancel":
                session = self.sessions.get(params["sessionId"], {})
                session["cancelled"] = True
                if (proc := session.get("process")) and proc.returncode is None:
                    os.killpg(proc.pid, signal.SIGTERM)
                return
            else:
                if ident is not None:
                    self.send({"id": ident, "error": {"code": -32601, "message": f"Unsupported method: {method}"}})
                return
            if ident is not None:
                self.send({"id": ident, "result": result})
        except Exception as exc:  # noqa: BLE001 - return ACP errors instead of crashing the transport
            if ident is not None:
                self.send({"id": ident, "error": {"code": -32603, "message": str(exc)}})

    async def run(self):
        reader = asyncio.StreamReader()
        await asyncio.get_running_loop().connect_read_pipe(lambda: asyncio.StreamReaderProtocol(reader), sys.stdin)
        try:
            while line := await reader.readline():
                try:
                    msg = json.loads(line)
                except json.JSONDecodeError:
                    self.send({"id": None, "error": {"code": -32700, "message": "Invalid JSON"}})
                    continue
                if "method" not in msg:
                    future = self.pending.get(msg.get("id"))
                    if future and not future.done():
                        if "error" in msg:
                            future.set_exception(RuntimeError(msg["error"]))
                        else:
                            future.set_result(msg.get("result", {}))
                    continue
                task = asyncio.create_task(self.dispatch(msg))
                self.tasks.add(task)
                task.add_done_callback(self.tasks.discard)
        finally:
            for session in self.sessions.values():
                if (proc := session.get("process")) and proc.returncode is None:
                    os.killpg(proc.pid, signal.SIGTERM)
            for task in self.tasks:
                task.cancel()
            await asyncio.gather(*self.tasks, return_exceptions=True)


if __name__ == "__main__":
    asyncio.run(Bridge().run())
