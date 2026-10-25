# /// script
# requires-python = ">=3.11"
# dependencies = ["tomlkit==0.13.3"]
# ///
"""Offline regression checks for private config merging and optional agent groups."""

import copy
import json
import os
import shutil
import subprocess
import tempfile
import tomllib
import unittest
from pathlib import Path

import tomlkit
from codex_config import render

ROOT = Path(__file__).resolve().parent.parent
DATA = {}
for path in (ROOT / ".chezmoidata").glob("*.toml"):
    DATA.update(tomllib.loads(path.read_text()))


def settings(codex=True, canary=True):
    data = copy.deepcopy(DATA)
    data["install"] = {"codex": codex, "canary": canary}
    return data


class CodexConfigTests(unittest.TestCase):
    def test_private_settings_and_comments_survive(self):
        current = """# private local configuration
model = "personal-model"
[projects."/private/example"]
trust_level = "trusted"
[mcp_servers.personal]
url = "https://example.invalid"
bearer_token_env_var = "PERSONAL_TOKEN"
[shell_environment_policy.set]
MY_LOCAL_SETTING = "keep"
[sandbox_workspace_write]
writable_roots = ["/private/local-root"]
[analytics]
enabled = true # previously enabled
"""
        result = render(current, settings())
        doc = tomlkit.parse(result)
        self.assertEqual(doc["model"], "personal-model")
        self.assertEqual(doc["projects"]["/private/example"]["trust_level"], "trusted")
        self.assertEqual(
            doc["mcp_servers"]["personal"]["bearer_token_env_var"], "PERSONAL_TOKEN"
        )
        self.assertEqual(
            doc["shell_environment_policy"]["set"]["MY_LOCAL_SETTING"], "keep"
        )
        self.assertFalse(doc["analytics"]["enabled"])
        self.assertEqual(
            doc["sandbox_workspace_write"]["writable_roots"],
            [
                "/private/local-root",
                str(Path.home() / ".Trash"),
            ],
        )
        self.assertIn("# private local configuration", result)
        self.assertEqual(render(result, settings()), result)

    def test_telemetry_does_not_install_codex_defaults(self):
        result = tomlkit.parse(render("model = 'local'\n", settings(), True))
        self.assertEqual(set(result), {"model", "analytics"})
        self.assertFalse(result["analytics"]["enabled"])

    def test_canary_off_disables_only_managed_transport(self):
        current = render("", settings())
        doc = tomlkit.parse(current)
        doc["mcp_servers"]["chrome-devtools"]["oauth"] = {"client_secret": "private"}
        result = tomlkit.parse(render(tomlkit.dumps(doc), settings(canary=False)))
        self.assertFalse(result["mcp_servers"]["chrome-devtools"]["enabled"])
        self.assertEqual(
            result["mcp_servers"]["chrome-devtools"]["oauth"]["client_secret"],
            "private",
        )
        local = '[mcp_servers.chrome-devtools]\ncommand="personal-command"\n'
        self.assertEqual(
            tomlkit.parse(render(local, settings(canary=False)))["mcp_servers"][
                "chrome-devtools"
            ]["command"],
            "personal-command",
        )

    def test_invalid_config_fails_instead_of_replacing_it(self):
        with self.assertRaises(tomlkit.exceptions.ParseError):
            render("[broken", settings())
        with self.assertRaises(TypeError):
            render('analytics = "not a table"', settings())


class AgentIntegrationTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="dotfiles-agents-")
        self.addCleanup(self.temporary.cleanup)
        self.temp = Path(self.temporary.name)
        self.config = self.temp / "chezmoi.toml"
        self.config.write_text('mode = "symlink"\n')
        self.base = [
            "chezmoi",
            "--source",
            str(ROOT),
            "--config",
            str(self.config),
            "--destination",
            str(self.temp / "home"),
            "--cache",
            str(self.temp / "cache"),
            "--persistent-state",
            str(self.temp / "state.boltdb"),
        ]

    def template(self, path, install):
        return subprocess.check_output(
            self.base
            + [
                "--override-data",
                json.dumps({"install": install}),
                "execute-template",
                "--file",
                str(ROOT / path),
            ],
            text=True,
        )

    def test_init_preserves_existing_groups_and_defaults_new_group_off(self):
        self.config.write_text(
            'mode = "symlink"\n[data.install]\nclaude = true\napps = true\n'
        )
        subprocess.run(
            self.base
            + [
                "--override-data",
                '{"install":{"codex":true}}',
                "init",
                "--promptDefaults",
                "--no-tty",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        actual = tomllib.loads(self.config.read_text())["data"]["install"]
        self.assertFalse(actual["codex"])
        self.assertTrue(actual["claude"])
        self.assertTrue(actual["apps"])

    def test_telemetry_command_never_reenters_chezmoi(self):
        mock_bin = self.temp / "bin"
        mock_bin.mkdir()
        blocked = mock_bin / "chezmoi"
        blocked.write_text("#!/bin/sh\nexit 97\n")
        blocked.chmod(0o755)
        env = {
            **os.environ,
            "PATH": f"{mock_bin}:{os.environ['PATH']}",
            "DOTFILES_SOURCE_DIR": str(ROOT),
            "CODEX_HOME": str(self.temp / "codex"),
        }
        command = next(
            c["run"] for c in DATA["telemetry"]["commands"] if c["tool"] == "codex"
        )
        subprocess.run(
            ["/bin/sh", "-c", command],
            env=env,
            check=True,
            capture_output=True,
            text=True,
        )
        doc = tomllib.loads((self.temp / "codex/config.toml").read_text())
        self.assertFalse(doc["analytics"]["enabled"])

    def test_apply_preserves_private_config_and_global_instructions(self):
        home = self.temp / "home"
        codex_home = home / ".codex"
        codex_home.mkdir(parents=True)
        config = codex_home / "config.toml"
        config.write_text('model = "personal-model" # keep me\n')
        instructions = codex_home / "AGENTS.md"
        instructions.write_text("Private global instructions\n")
        command = self.base + [
            "--override-data",
            '{"install":{"codex":true,"canary":true}}',
            "apply",
            "--exclude",
            "scripts",
            "--force",
            str(config),
            str(instructions),
        ]
        subprocess.run(command, check=True, capture_output=True, text=True)
        content = config.read_text()
        doc = tomllib.loads(content)
        self.assertEqual(doc["model"], "personal-model")
        self.assertEqual(
            doc["shell_environment_policy"]["set"]["DOTFILES_AGENT"], "codex"
        )
        self.assertTrue(doc["mcp_servers"]["chrome-devtools"]["enabled"])
        self.assertIn("# keep me", content)
        self.assertTrue(
            instructions.read_text().startswith("Private global instructions\n")
        )
        self.assertIn(DATA["agents"]["instructions"], instructions.read_text())
        instructions_content = instructions.read_text()
        self.assertFalse(config.is_symlink())
        self.assertEqual(config.stat().st_mode & 0o777, 0o600)
        subprocess.run(command, check=True, capture_output=True, text=True)
        self.assertEqual(config.read_text(), content)
        self.assertEqual(instructions.read_text(), instructions_content)
        if shutil.which("codex"):
            result = subprocess.run(
                ["codex", "features", "list"],
                env={**os.environ, "CODEX_HOME": str(codex_home)},
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertRegex(result.stdout, r"prevent_idle_sleep\s+experimental\s+true")

    def test_agent_group_combinations(self):
        # Empty install also represents a Mac that hasn't answered the new prompt.
        for install in (
            {},
            {"claude": True},
            {"codex": True},
            {"claude": True, "codex": True},
        ):
            for canary in (False, True):
                with self.subTest(install=install, canary=canary):
                    install = {**install, "canary": canary}
                    managed = subprocess.check_output(
                        self.base
                        + [
                            "--override-data",
                            json.dumps({"install": install}),
                            "managed",
                            "--include",
                            "files",
                        ],
                        text=True,
                    ).splitlines()
                    self.assertEqual(
                        ".codex/config.toml" in managed, bool(install.get("codex"))
                    )
                    self.assertEqual(
                        ".codex/AGENTS.md" in managed, bool(install.get("codex"))
                    )
                    self.assertEqual(
                        ".claude/settings.json" in managed, bool(install.get("claude"))
                    )
                    self.assertEqual(
                        ".claude/CLAUDE.md" in managed, bool(install.get("claude"))
                    )
                    enabled = bool(install.get("claude") or install.get("codex"))
                    self.assertEqual(".local/agent-bin/rm" in managed, enabled)
                    self.assertEqual(".local/bin/agent-sleep-guard" in managed, enabled)
                    self.assertFalse(any(p.startswith("scripts/") for p in managed))
                    guard = self.template(
                        "run_after_agent-sleep-guard.sh.tmpl", install
                    )
                    self.assertEqual('cat >"$tmp/agent"' in guard, enabled)
                    self.assertEqual(
                        self.template("dot_claude/modify_CLAUDE.md.tmpl", install),
                        self.template(
                            "private_dot_codex/modify_AGENTS.md.tmpl", install
                        ),
                    )
                    for path in ROOT.glob("run_*.tmpl"):
                        rendered = self.template(path.name, install)
                        subprocess.run(
                            ["shellcheck", "-s", "bash", "-"],
                            input=rendered,
                            text=True,
                            check=True,
                        )

    def test_minimal_shell_path_and_agent_rm(self):
        home = self.temp / "home"
        home.mkdir()
        for source, target in (
            ("dot_zshenv", ".zshenv"),
            ("dot_zprofile", ".zprofile"),
        ):
            (home / target).symlink_to(ROOT / source)
        agent_bin = home / ".local/agent-bin"
        agent_bin.mkdir(parents=True)
        shutil.copyfile(ROOT / "dot_local/agent-bin/executable_rm", agent_bin / "rm")
        (agent_bin / "rm").chmod(0o755)
        for marker in ({}, {"CLAUDECODE": "1"}, {"DOTFILES_AGENT": "codex"}):
            for mode in ("-c", "-lc"):
                with self.subTest(marker=marker, mode=mode):
                    env = {
                        "HOME": str(home),
                        "USER": os.environ.get("USER", "runner"),
                        "PATH": "/usr/bin:/bin:/usr/sbin:/sbin",
                        **marker,
                    }
                    output = subprocess.check_output(
                        [
                            "/bin/zsh",
                            mode,
                            "command -v brew mise uv; print -r -- $path; whence -p rm",
                        ],
                        env=env,
                        text=True,
                    ).splitlines()
                    self.assertEqual(
                        output[:3],
                        [
                            f"/opt/homebrew/bin/{tool}"
                            for tool in ("brew", "mise", "uv")
                        ],
                    )
                    self.assertIn(str(home / ".local/share/mise/shims"), output[3])
                    expected = str(agent_bin / "rm") if marker else "/bin/rm"
                    self.assertEqual(output[-1], expected)

    def test_sleep_detection_uses_active_assertions(self):
        mock_bin = self.temp / "bin"
        mock_bin.mkdir()
        for name, body in {
            "pmset": '#!/bin/sh\nprintf "%s\\n" "$ASSERTIONS"\n',
            "pgrep": "#!/bin/sh\nexit 1\n",
        }.items():
            path = mock_bin / name
            path.write_text(body)
            path.chmod(0o755)
        for assertion, active in (
            ("", False),
            (
                'pid 123(codex): [0x01] 00:01:00 PreventUserIdleSystemSleep named: "Codex"',
                True,
            ),
            (
                'pid 123(other): [0x01] 00:01:00 PreventUserIdleSystemSleep named: "other"',
                False,
            ),
            (
                'pid 123(codex): [0x01] 00:01:00 PreventUserIdleDisplaySleep named: "Codex"',
                False,
            ),
        ):
            with self.subTest(assertion=assertion):
                result = subprocess.run(
                    [
                        "bash",
                        str(ROOT / "dot_local/bin/executable_agent-sleep-guard"),
                        "--check",
                    ],
                    env={
                        **os.environ,
                        "PATH": f"{mock_bin}:/usr/bin:/bin",
                        "ASSERTIONS": assertion,
                    },
                    capture_output=True,
                    text=True,
                    check=False,
                )
                self.assertEqual(result.returncode, 0 if active else 1)
                self.assertEqual(result.stdout, "")

    def test_sleep_guard_battery_grace_and_release(self):
        mock_bin = self.temp / "bin"
        mock_bin.mkdir()
        for name, body in {
            "pmset": """#!/bin/sh
case "$*" in
  '-g assertions') printf '%s\\n' "$ASSERTIONS" ;;
  '-g batt') printf '%s\\n' "$BATTERY" ;;
  '-g') echo "SleepDisabled $HELD" ;;
esac
""",
            "pgrep": "#!/bin/sh\nexit 1\n",
            "sudo": """#!/bin/sh
[ "$2" = -l ] && exit 0
printf '%s\\n' "$*" >> "$SUDO_LOG"
""",
        }.items():
            path = mock_bin / name
            path.write_text(body)
            path.chmod(0o755)
        state_home = self.temp / "state"
        log = self.temp / "sudo.log"
        env = {
            **os.environ,
            "PATH": f"{mock_bin}:/usr/bin:/bin",
            "XDG_STATE_HOME": str(state_home),
            "SUDO_LOG": str(log),
            "ASSERTIONS": 'pid 123(codex): [0x1] PreventUserIdleSystemSleep named: "Codex"',
            "BATTERY": "Now drawing from 'AC Power' 80%",
            "HELD": "0",
        }
        command = ["bash", str(ROOT / "dot_local/bin/executable_agent-sleep-guard")]
        subprocess.run(command, env=env, check=True, capture_output=True, text=True)
        self.assertIn("disablesleep 1", log.read_text())
        # An idle client within the grace period retains the hold.
        log.write_text("")
        env.update(ASSERTIONS="", HELD="1")
        subprocess.run(command, env=env, check=True, capture_output=True, text=True)
        self.assertEqual(log.read_text(), "")
        # Expired grace releases the hold, including after a crashed client.
        (state_home / "agent-sleep-guard/last-working").write_text("0")
        subprocess.run(command, env=env, check=True, capture_output=True, text=True)
        self.assertIn("disablesleep 0", log.read_text())
        # Low battery wins even while an agent is actively working.
        log.write_text("")
        env.update(
            ASSERTIONS="pid 123(codex): [0x1] PreventUserIdleSystemSleep",
            BATTERY="Now drawing from 'Battery Power' 20%",
        )
        subprocess.run(command, env=env, check=True, capture_output=True, text=True)
        self.assertIn("disablesleep 0", log.read_text())


if __name__ == "__main__":
    unittest.main()
