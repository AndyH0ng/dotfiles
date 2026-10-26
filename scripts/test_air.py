"""Air setup must preserve the user's other agents and VM settings."""

import importlib.util
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SOURCE = Path(__file__).parent / "air/setup.py"
spec = importlib.util.spec_from_file_location("air_setup", SOURCE)
assert spec and spec.loader
setup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(setup)


class AirSetupTests(unittest.TestCase):
    def test_merges_private_settings_idempotently(self):
        with tempfile.TemporaryDirectory() as scratch:
            root = Path(scratch)
            config = root / "Air"
            config.mkdir()
            acp = config / "acp.json"
            acp.write_text(json.dumps({"agent_servers": {
                "Other Agent": {"command": "other", "env": {"TOKEN": "private"}},
                "Claude Code": {"env": {"LOCAL_SETTING": "kept"}},
            }}))
            options = config / "Air.vmoptions"
            options.write_text("-Xmx1G\n-Dkeep=true\n")
            with patch.object(setup, "AIR_CONFIG", config), patch.object(
                setup, "BRIDGE_DIR", root / "bridge"
            ):
                setup.configure_acp()
                setup.configure_vmoptions()
                first = acp.read_bytes(), options.read_bytes()
                setup.configure_acp()
                setup.configure_vmoptions()
                self.assertEqual(first, (acp.read_bytes(), options.read_bytes()))

            data = json.loads(acp.read_text())["agent_servers"]
            self.assertEqual(data["Other Agent"]["env"]["TOKEN"], "private")
            self.assertEqual(data["Claude Code"]["env"]["LOCAL_SETTING"], "kept")
            self.assertEqual(data["Claude Code"]["args"], [str(root / "bridge/bridge.py")])
            self.assertIn("-Xmx1G\n-Dkeep=true\n", options.read_text())
            self.assertEqual(options.read_text().count("-javaagent:"), 1)


if __name__ == "__main__":
    unittest.main()
