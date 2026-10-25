# /// script
# requires-python = ">=3.11"
# dependencies = ["tomlkit==0.13.3"]
# ///
"""Merge public Codex defaults without exporting private settings or comments."""

import argparse
import json
import os
import subprocess
import sys
import tempfile
import tomllib
from collections.abc import Mapping
from pathlib import Path

import tomlkit


def merge(target, settings):
    for key, value in settings.items():
        if isinstance(value, Mapping):
            if key not in target:
                target[key] = tomlkit.table()
            if not isinstance(target[key], Mapping):
                raise TypeError(f"expected a TOML table at {key}")
            merge(target[key], value)
        else:
            target[key] = value


def render(current, data, telemetry_only=False):
    doc = tomlkit.parse(current)
    merge(doc, data["telemetry"]["codex"])
    if not telemetry_only and data.get("install", {}).get("codex", False):
        defaults = dict(data["codex"])
        sandbox = dict(defaults.get("sandbox_workspace_write", {}))
        if "writable_roots" in sandbox:
            existing = doc.get("sandbox_workspace_write", {}).get("writable_roots", [])
            sandbox["writable_roots"] = list(
                dict.fromkeys(
                    [
                        *existing,
                        *(
                            os.path.expanduser(root)
                            for root in sandbox["writable_roots"]
                        ),
                    ]
                )
            )
            defaults["sandbox_workspace_write"] = sandbox
        merge(doc, defaults)
        servers = doc.get("mcp_servers", {})
        spec = data["mcp"]["chrome_devtools"]
        if data.get("install", {}).get("canary", False):
            merge(doc, {"mcp_servers": {"chrome-devtools": {**spec, "enabled": True}}})
        elif "chrome-devtools" in servers:
            server = servers["chrome-devtools"]
            # Disable only our transport; leave personal MCPs and credentials alone.
            if all(server.get(key) == value for key, value in spec.items()):
                server["enabled"] = False
    return tomlkit.dumps(doc)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--telemetry-only", action="store_true")
    parser.add_argument("--check", action="store_true")
    parser.add_argument(
        "--data-file", type=Path, help="rendered public settings from chezmoi"
    )
    parser.add_argument("--file", type=Path, help="otherwise transform stdin to stdout")
    args = parser.parse_args()
    if args.data_file:
        data = json.loads(args.data_file.read_text())
    elif args.telemetry_only:
        # The before-script holds chezmoi's state lock. Read source data directly
        # instead of recursively invoking chezmoi while that lock is held.
        source = Path(__file__).resolve().parent.parent
        data = tomllib.loads((source / ".chezmoidata/telemetry.toml").read_text())
    else:
        data = json.loads(
            subprocess.check_output(["chezmoi", "data", "--format", "json"])
        )
    current = (
        args.file.read_text()
        if args.file and args.file.exists()
        else ""
        if args.file
        else sys.stdin.read()
    )
    try:
        result = render(current, data, args.telemetry_only)
    except (ValueError, TypeError, tomlkit.exceptions.ParseError):
        # Parse errors can contain private input; never echo them into logs.
        sys.exit("codex-config: invalid TOML or a managed table has the wrong type")
    if args.check:
        return int(tomlkit.parse(current).unwrap() != tomlkit.parse(result).unwrap())
    if not args.file:
        sys.stdout.write(result)
    elif current != result:
        args.file.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        # Atomic replacement, with private permissions even on a fresh machine.
        fd, temporary = tempfile.mkstemp(dir=args.file.parent, prefix=".codex-config-")
        try:
            with os.fdopen(fd, "w") as output:
                output.write(result)
            os.replace(temporary, args.file)
        finally:
            if os.path.exists(temporary):
                os.unlink(temporary)
    return 0


if __name__ == "__main__":
    sys.exit(main())
