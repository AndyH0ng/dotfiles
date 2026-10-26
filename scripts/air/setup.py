"""Install the local Claude Code ACP bridge without replacing Air's private settings."""

import hashlib
import json
import subprocess
import tempfile
from pathlib import Path

SOURCE = Path(__file__).resolve().parent
HOME = Path.home()
AIR_APP = Path("/Applications/Air.app")
AIR_CONFIG = HOME / "Library/Application Support/JetBrains/Air"
BRIDGE_DIR = HOME / ".local/share/air-claude-bridge"
PYTHON = Path("/opt/homebrew/bin/python3")
CLAUDE = Path("/opt/homebrew/bin/claude")


def write_if_changed(path: Path, content: bytes, mode: int) -> bool:
    if path.exists() and path.read_bytes() == content:
        return False
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, prefix=".dotfiles-air-", delete=False) as staged:
        temp_path = Path(staged.name)
        staged.write(content)
    try:
        temp_path.chmod(path.stat().st_mode & 0o777 if path.exists() else mode)
        temp_path.replace(path)
    finally:
        temp_path.unlink(missing_ok=True)
    return True


def source_fingerprint() -> str:
    source_files = [SOURCE / "bridge.py", SOURCE / "build.sh", SOURCE / "agent.mf"]
    source_files.extend(sorted((SOURCE / "src/airui2").glob("*.java")))
    digest = hashlib.sha256()
    for path in source_files:
        digest.update(path.name.encode())
        digest.update(path.read_bytes())
    return digest.hexdigest()


def install_bridge() -> None:
    BRIDGE_DIR.mkdir(parents=True, exist_ok=True, mode=0o700)
    write_if_changed(BRIDGE_DIR / "bridge.py", (SOURCE / "bridge.py").read_bytes(), 0o700)
    fingerprint = source_fingerprint()
    marker = BRIDGE_DIR / ".dotfiles-source.sha256"
    output_files = [BRIDGE_DIR / "ui2/usage-agent.jar", BRIDGE_DIR / "ui2/usage-helper2.jar"]
    if not all(path.exists() for path in output_files) or not marker.exists() or marker.read_text() != fingerprint + "\n":
        with tempfile.TemporaryDirectory(prefix="air-claude-build-") as scratch:
            subprocess.run(["/bin/bash", str(SOURCE / "build.sh"), scratch], check=True)
            for filename, destination in zip(("usage-agent.jar", "usage-helper2.jar"), output_files):
                write_if_changed(destination, (Path(scratch) / filename).read_bytes(), 0o644)
        write_if_changed(marker, (fingerprint + "\n").encode(), 0o600)


def configure_acp() -> None:
    path = AIR_CONFIG / "acp.json"
    data = json.loads(path.read_text()) if path.exists() else {}
    servers = data.setdefault("agent_servers", {})
    agent = servers.setdefault("Claude Code", {})
    agent["command"] = str(PYTHON)
    agent["args"] = [str(BRIDGE_DIR / "bridge.py")]
    env = agent.setdefault("env", {})
    env["AIR_CLAUDE_BIN"] = str(CLAUDE)
    env["AIR_CLAUDE_BRIDGE_VERSION"] = "0.4.0"
    write_if_changed(path, (json.dumps(data, ensure_ascii=False, indent=2) + "\n").encode(), 0o600)


def configure_vmoptions() -> None:
    path = AIR_CONFIG / "Air.vmoptions"
    if path.exists():
        existing = path.read_text()
    else:
        bundled = AIR_APP / "Contents/Resources/Air.vmoptions"
        existing = bundled.read_text()
    agent_line = f"-javaagent:{BRIDGE_DIR / 'ui2/usage-agent.jar'}"
    if existing.splitlines().count(agent_line) == 1 and not any(
        line.startswith("-javaagent:") and
        "air-claude-bridge/ui2/usage-agent.jar" in line and line != agent_line
        for line in existing.splitlines()
    ):
        return
    lines = [line for line in existing.splitlines()
             if not (line.startswith("-javaagent:") and
                     "air-claude-bridge/ui2/usage-agent.jar" in line)]
    lines.append(agent_line)
    write_if_changed(path, ("\n".join(lines) + "\n").encode(), 0o644)


def main() -> None:
    if not AIR_APP.is_dir():
        raise SystemExit("JetBrains Air is not installed; enable the ide group first")
    if not PYTHON.is_file() or not CLAUDE.exists():
        raise SystemExit("Air's Claude bridge requires Homebrew Python and Claude Code")
    install_bridge()
    configure_acp()
    configure_vmoptions()


if __name__ == "__main__":
    main()
