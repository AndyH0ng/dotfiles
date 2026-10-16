# AGENTS.md

Guide for AI agents (and humans) changing this dotfiles repo. It is managed with [chezmoi](https://chezmoi.io) and published as a **public** repo, so anything committed is public.

`README.md` (Korean) is the user-facing overview: what's installed and how to use it. Conventions, pitfalls and checks live **only** here, so don't duplicate them into the README.

The repo targets **Apple Silicon macOS** only. `/opt/homebrew` is hardcoded on purpose.

## Layout

| Path | What it is |
|---|---|
| `dot_*`, `dot_config/…` | Files that land in `$HOME` (`dot_zshrc` → `~/.zshrc`) |
| `*.tmpl` | Templates, rendered by chezmoi |
| `.chezmoidata/theme.toml` | **Only** place colors and theme names are defined |
| `.chezmoi.toml.tmpl` | chezmoi config: symlink mode, one prompt per optional group |
| `.chezmoidata/packages.toml` | **Only** place optional Brewfile groups are defined |
| `Brewfile` | Everything the dotfiles depend on |
| `Brewfile.<group>` | One file per optional group (opt-in, stored as `.install.<group>`). Keep them to essentials only. |
| `uv-tools.txt` | Python CLIs installed with `uv tool install` (e.g. pyrefly) |
| `run_onchange_before_install-packages.sh.tmpl` | brew bundle, tap trust, uv tools, gh/codex telemetry, git hooks path |
| `run_onchange_after_setup.sh.tmpl` | `mise install`, tinty theme, Claude Code MCP servers (`claude mcp add`) |
| `claude-settings.json` | Source for `~/.claude/settings.json` (see below) |
| `dot_local/bin/` | `claude-sync`, `dotfiles-doctor` |
| `.githooks/pre-commit` | gitleaks on staged changes |
| `.github/workflows/test.yml` | Fresh-macOS install test, weekly + on push |

## How edits flow

- **Symlink mode.** Plain files in `$HOME` are symlinks into this repo. Editing `~/.zshrc` edits `dot_zshrc` directly, and you never need `chezmoi re-add`.
- **Templates are copies.** Edit the `.tmpl` in the repo, then run `chezmoi apply`. Never edit the rendered file in `$HOME`, because the next apply overwrites it.
- **`executable_` files are copies too**, not symlinks: `~/.local/bin/dotfiles-doctor`, `~/.local/bin/claude-sync`, `~/.claude/statusline.sh`. Edit them in `dot_local/bin/` / `dot_claude/` and run `chezmoi apply`.
- **Colors.** Never hardcode a hex color or theme name. Add a key to `.chezmoidata/theme.toml` and reference it as `{{ .theme.palette.base0D }}` from a `.tmpl`. Shell-side consumers read `~/.config/zsh/theme.zsh`. Tools that accept ANSI color names (delta's `base16` theme, zsh-patina) should use those so they follow the palette automatically. When changing `theme.scheme`, copy the palette from tinty's scheme yaml, and `dotfiles-doctor` flags any mismatch.
- **Claude Code settings.** `dot_claude/modify_settings.json.tmpl` merges `claude-settings.json` with the machine's local `autoMode` block, which holds private repo names and never goes in git. After changing settings via `/config`, run `claude-sync`.
- **Machine-specific values** go in templates gated by `stat` or data (see `dot_config/git/local.tmpl`, where commit signing is only enabled if the key exists). Never hardcode `/Users/<name>`. Use `$HOME`, `~`, or `{{ .chezmoi.homeDir }}`.

## Adding things

- **A config file:** `chezmoi add <path>`. Then check the source name has no `private_` or `executable_` prefix it doesn't need, because those attributes make chezmoi **copy** instead of symlink. `chmod 644` the target if needed.
- **A package:** add it to `Brewfile` if a dotfile depends on it, otherwise to the matching optional `Brewfile.*` — only if it's genuinely essential. Packages installed by hand and not listed anywhere are fine; `dotfiles-doctor` lists them as informational.
  - A new optional group needs two things: a `[[groups]]` entry in `.chezmoidata/packages.toml` and a `Brewfile.<name>`. Nothing else, because the prompt, install script, doctor and CI all loop over that list. Then run `chezmoi init` to answer the new prompt. Descriptions must not contain commas.
  - `.chezmoi.toml.tmpl` can't see `.chezmoidata` (it isn't loaded at init yet), so it reads `packages.toml` with `include … | fromToml`.
  - CI uses `chezmoi init --promptDefaults` (every group off). Don't use `--promptBool`: it matches by prompt text and splits on commas.
  - Formulae from third-party taps need an explicit `brew trust --formula|--cask <full/name>` line in the install script (Homebrew 7 refuses untrusted taps). Trust the single formula, not the whole tap.
  - The install script uses `brew bundle --no-upgrade`. Without it, every Brewfile change also upgrades every outdated package (minutes of big cask downloads). Upgrades are a separate, deliberate `brew upgrade`.
  - Python CLIs not in Homebrew go in `uv-tools.txt`. The install script installs them and `dotfiles-doctor` checks them.
  - Some formulae aren't binaries (e.g. `antidote` is a sourced zsh script). Don't check them with `command -v`.
- **A shell shortcut:** if it shadows a standard command (`ls`, `cat`, `grep`, `find`), use `zsh-abbr` in `dot_zshrc`, not `alias`, so agents and pasted commands still get the real tool. New names (`ll`, `la`) can be plain aliases. Shadowing an existing command needs `abbr -S -q -f … >/dev/null` (without `-f` it silently refuses).
- **An env var for every shell** (PATH, telemetry opt-outs): `dot_zshenv`. Agents run non-interactive shells, so PATH set in `.zshrc` is invisible to them. Login-shell-only setup: `dot_zprofile`. Interactive-only: `dot_zshrc`.

## Before committing

1. `chezmoi apply` (or `chezmoi diff` for a dry run).
2. Run `dotfiles-doctor --json` and expect `"ok": true` once committed and pushed. Each failing field has a hint in `fixes`. `brew.unlisted` is informational, listing packages the user installed by hand, so never add them to a Brewfile unless asked.
3. Commit. The pre-commit hook runs gitleaks, and you shouldn't bypass it.
4. Push. Don't block on CI (the macOS run takes 2–5 min), so check `gh run list -L1` later. CI installs from scratch with every optional group off, requires `dotfiles-doctor` to be clean, resolves every name in the optional Brewfiles, and shellchecks all rendered scripts. A red CI means a new Mac would break.

Note: `claude-code@latest` and `chrome-devtools-mcp@latest` float on purpose, so a weekly CI failure may come from a new upstream release rather than a change here.

## Don'ts

- Don't commit `~/.claude.json`, history, tokens, `autoMode`, or anything under `~/.ssh` except public-safe references.
- Don't edit rendered template output in `$HOME`.
- Don't add aliases that shadow POSIX tools.
- Don't pin third-party GitHub Actions by tag only. Prefer first-party actions or run the tool directly.
