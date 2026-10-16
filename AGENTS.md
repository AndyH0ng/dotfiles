# AGENTS.md

Guide for AI agents (and humans) changing this dotfiles repo. It is managed with [chezmoi](https://chezmoi.io) and published as a **public** repo, so anything committed is public.

## Layout

| Path | What it is |
|---|---|
| `dot_*`, `dot_config/…` | Files that land in `$HOME` (`dot_zshrc` → `~/.zshrc`) |
| `*.tmpl` | Templates, rendered by chezmoi |
| `.chezmoidata/theme.toml` | **Only** place colors and theme names are defined |
| `.chezmoi.toml.tmpl` | chezmoi config: symlink mode, `installExtras` prompt |
| `Brewfile` | Everything the dotfiles depend on |
| `Brewfile.extra` | Personal apps, fonts and extra CLIs (opt-in via `installExtras`) |
| `run_onchange_before_install-packages.sh.tmpl` | brew bundle, tap trust, gh/codex telemetry, git hooks path |
| `run_onchange_after_setup.sh.tmpl` | `mise install`, tinty theme |
| `claude-settings.json` | Source for `~/.claude/settings.json` (see below) |
| `dot_local/bin/` | `claude-sync`, `dotfiles-doctor` |
| `.githooks/pre-commit` | gitleaks on staged changes |
| `.github/workflows/test.yml` | Fresh-macOS install test, weekly + on push |

## How edits flow

- **Symlink mode.** Plain files in `$HOME` are symlinks into this repo. Editing `~/.zshrc` edits `dot_zshrc` directly, and you never need `chezmoi re-add`.
- **Templates are copies.** Edit the `.tmpl` in the repo, then run `chezmoi apply`. Never edit the rendered file in `$HOME`, because the next apply overwrites it.
- **Colors.** Never hardcode a hex color or theme name. Add a key to `.chezmoidata/theme.toml` and reference it as `{{ .theme.palette.base0D }}` from a `.tmpl`. Shell-side consumers read `~/.config/zsh/theme.zsh`.
- **Claude Code settings.** `dot_claude/modify_settings.json.tmpl` merges `claude-settings.json` with the machine's local `autoMode` block, which holds private repo names and never goes in git. After changing settings via `/config`, run `claude-sync`.
- **Machine-specific values** go in templates gated by `stat` or data (see `dot_config/git/local.tmpl`, where commit signing is only enabled if the key exists). Never hardcode `/Users/<name>`. Use `$HOME`, `~`, or `{{ .chezmoi.homeDir }}`.

## Adding things

- **A config file:** `chezmoi add <path>`. Then check the source name has no `private_` or `executable_` prefix it doesn't need, because those attributes make chezmoi **copy** instead of symlink. `chmod 644` the target if needed.
- **A package:** add it to `Brewfile` if a dotfile depends on it, otherwise to `Brewfile.extra`.
  - Formulae from third-party taps need an explicit `brew trust --formula|--cask <full/name>` line in the install script (Homebrew 7 refuses untrusted taps). Trust the single formula, not the whole tap.
  - The install script uses `brew bundle --no-upgrade`. Without it, every Brewfile change also upgrades every outdated package (minutes of big cask downloads). Upgrades are a separate, deliberate `brew upgrade`.
  - Some formulae aren't binaries (e.g. `antidote` is a sourced zsh script). Don't check them with `command -v`.
- **A shell shortcut:** use `zsh-abbr` in `dot_zshrc`, not `alias`, so agents and pasted commands still get the real tool. Shadowing an existing command needs `abbr -S -q -f … >/dev/null` (without `-f` it silently refuses).
- **An env var for every shell** (PATH, telemetry opt-outs): `dot_zshenv`. Login-shell-only setup: `dot_zprofile`. Interactive-only: `dot_zshrc`.

## Before committing

1. `chezmoi apply` (or `chezmoi diff` for a dry run).
2. Run `dotfiles-doctor --json` and expect `"ok": true` once committed and pushed. Each failing field has a hint in `fixes`.
3. Commit. The pre-commit hook runs gitleaks, and you shouldn't bypass it.
4. Push, then `gh run watch` the CI run. CI installs from scratch on macOS with `installExtras=false`. A red CI means a new Mac would break.

## Don'ts

- Don't commit `~/.claude.json`, history, tokens, `autoMode`, or anything under `~/.ssh` except public-safe references.
- Don't edit rendered template output in `$HOME`.
- Don't add aliases that shadow POSIX tools.
- Don't pin third-party GitHub Actions by tag only. Prefer first-party actions or run the tool directly.
