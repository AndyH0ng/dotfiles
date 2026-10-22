# AGENTS.md

Guide for AI agents (and humans) changing this dotfiles repo. It is managed with [chezmoi](https://chezmoi.io) and published as a **public** repo, so anything committed is public.

`README.md` (Korean) is the user-facing overview: what's installed and how to use it. Conventions, pitfalls and checks live **only** here, so don't duplicate them into the README.

The repo targets **Apple Silicon macOS** only. `/opt/homebrew` is hardcoded on purpose.

## Layout

| Path | What it is |
|---|---|
| `dot_*`, `dot_config/…` | Files that land in `$HOME` (`dot_zshrc` → `~/.zshrc`) |
| `*.tmpl` | Templates, rendered by chezmoi |
| `.chezmoidata/theme.toml` | **Only** place colors, theme names and terminal fonts are defined |
| `.chezmoi.toml.tmpl` | chezmoi config: symlink mode, one prompt per optional group |
| `.chezmoiversion` | Minimum chezmoi version; older ones refuse to apply |
| `.chezmoiremove` | `$HOME` targets to delete on apply (removed or renamed sources) |
| `.chezmoidata/packages.toml` | **Only** place optional Brewfile groups are defined |
| `.chezmoidata/telemetry.toml` | **Only** place telemetry opt-outs are defined (env vars, Claude env, per-tool commands + checks) |
| `.chezmoidata/macos.toml` | **Only** place macOS `defaults` and default apps (duti) are defined |
| `Brewfile` | Everything the dotfiles depend on |
| `Brewfile.<group>` | One file per optional group (opt-in, stored as `.install.<group>`). Keep them to essentials only. |
| `uv-tools.txt` | Python CLIs installed with `uv tool install` (e.g. pyrefly) |
| `run_onchange_before_install-packages.sh.tmpl` | brew bundle, tap trust, uv tools, gh/codex telemetry, Claude Code MCP servers, git hooks path |
| `run_onchange_after_macos-defaults.sh.tmpl` | `defaults write` and `duti -s` for each entry in `macos.toml`, then relaunches apps |
| `run_after_agent-sleep-guard.sh.tmpl` | Keeps the Mac awake through a lid close while Claude Code works. Installs a sudoers entry for `pmset disablesleep`, a boot-time reset daemon and a LaunchAgent; a no-op once installed |
| `run_onchange_after_setup.sh.tmpl` | `mise install --locked`, tinty theme |
| `dot_config/mise/mise.lock` | Exact runtime versions + checksums that `latest`/`lts` resolve to |
| `claude-settings.json` | Source for `~/.claude/settings.json` (see below) |
| `dot_local/bin/` | `claude-sync`, `dotfiles-doctor`, `agent-sleep-guard` |
| `dot_local/agent-bin/` | Commands that replace standard ones for AI agents only (`rm` → Trash). `dot_zshenv` and `dot_zprofile` put it first on PATH when `$CLAUDECODE` is set |
| `.githooks/lint` | shellcheck (rendered scripts), actionlint, zizmor. Run by pre-commit **and** CI |
| `.githooks/pre-commit` | `.githooks/lint`, then gitleaks on staged changes |
| `.github/workflows/test.yml` | Fresh-macOS install test, weekly + on push |

## How edits flow

- **Symlink mode.** Plain files in `$HOME` are symlinks into this repo. Editing `~/.zshrc` edits `dot_zshrc` directly, and you never need `chezmoi re-add`.
- **Templates are copies.** Edit the `.tmpl` in the repo, then run `chezmoi apply`. Never edit the rendered file in `$HOME`, because the next apply overwrites it.
- **`executable_` files are copies too**, not symlinks: `~/.local/bin/dotfiles-doctor`, `~/.local/bin/claude-sync`, `~/.claude/statusline.sh`. Edit them in `dot_local/bin/` / `dot_claude/` and run `chezmoi apply`.
- **Colors.** Never hardcode a hex color or theme name. Add a key to `.chezmoidata/theme.toml` and reference it as `{{ .theme.palette.base0D }}` from a `.tmpl`. Shell-side consumers read `~/.config/zsh/theme.zsh`. Tools that accept ANSI color names (delta's `base16` theme, zsh-patina) should use those so they follow the palette automatically. When changing `theme.scheme`, copy the palette from tinty's scheme yaml, and `dotfiles-doctor` flags any mismatch.
- **Fonts.** Terminal fonts live in `theme.font` in `theme.toml`, and Ghostty is rendered from it. Changing a font means editing `theme.font.families` **and** its `cask` in `Brewfile`. `dotfiles-doctor` fails if they disagree or Ghostty can't find the family name, because Ghostty otherwise silently falls back to a default font.
- **Claude Code settings.** `dot_claude/modify_settings.json.tmpl` builds `~/.claude/settings.json` from `claude-settings.json`, adds `telemetry.claude_env`, and keeps the machine's local `autoMode` block, which holds private repo names and never goes in git. `claude-sync` does the reverse and strips both. After changing settings via `/config`, run `claude-sync`. Never add telemetry keys to `claude-settings.json` directly.
- **Git merges.** `~/.config/git/attributes` routes supported languages to mergiraf (driver in `dot_gitconfig`); it falls back to normal zdiff3 markers when it can't solve a conflict. Regenerate the attributes with `mergiraf languages --gitattributes` after upgrading mergiraf.
- **Machine-specific values** go in templates gated by `stat` or data (see `dot_config/git/local.tmpl`, where commit signing is only enabled if the key exists). Never hardcode `/Users/<name>`. Use `$HOME`, `~`, or `{{ .chezmoi.homeDir }}`.
- **Machine-local shell config and secrets** go in `~/.zshrc.local` / `~/.zshenv.local` (sourced if present, never in git), not in `dot_zshrc`. Installers that append lines to `~/.zprofile` etc. are editing the repo through the symlink, so move those lines there too.
- **Runtime versions.** `mise.lock` freezes what `latest`/`lts` mean, and the after-script installs with `--locked`, so every Mac gets the same versions. `minimum_release_age = "7d"` keeps brand-new releases out. To move forward: `mise lock -g --bump && mise install`, then commit `mise.lock`. After adding a tool to `config.toml`, run `mise lock -g` or `--locked` fails. `~/.config/mise/mise.lock` is a symlink and mise writes through it, so it stays in sync.

## Adding things

- **Removing or renaming a config file:** add the old target (relative to `$HOME`) to `.chezmoiremove`, or `$HOME` keeps a dangling symlink into the repo. `dotfiles-doctor` reports those as `dangling_symlinks`.
- **A newer chezmoi feature:** bump `.chezmoiversion` to the release that added it.
- **An empty file** (e.g. `~/.hushlogin`): chezmoi skips empty sources unless they're named `empty_dot_…`.
- **A config file:** `chezmoi add <path>`. Then check the source name has no `private_` or `executable_` prefix it doesn't need, because those attributes make chezmoi **copy** instead of symlink. `chmod 644` the target if needed.
- **A package:** add it to `Brewfile` if a dotfile depends on it, otherwise to the matching optional `Brewfile.*` — only if it's genuinely essential. Packages installed by hand and not listed anywhere are fine; `dotfiles-doctor` lists them as informational.
  - A new optional group needs two things: a `[[groups]]` entry in `.chezmoidata/packages.toml` and a `Brewfile.<name>`. Nothing else, because the prompt, install script, doctor and CI all loop over that list. Then run `chezmoi init` to answer the new prompt. Descriptions must not contain commas.
  - `.chezmoi.toml.tmpl` can't see `.chezmoidata` (it isn't loaded at init yet), so it reads `packages.toml` with `include … | fromToml`.
  - CI uses `chezmoi init --promptDefaults` (every group off). Don't use `--promptBool`: it matches by prompt text and splits on commas.
  - Formulae from third-party taps need an explicit `brew trust --formula|--cask <full/name>` line in the install script (Homebrew 7 refuses untrusted taps). Trust the single formula, not the whole tap.
  - The install script uses `brew bundle --no-upgrade`. Without it, every Brewfile change also upgrades every outdated package (minutes of big cask downloads). Upgrades are a separate, deliberate `brew upgrade`.
  - Python CLIs not in Homebrew go in `uv-tools.txt`. The install script installs them and `dotfiles-doctor` checks them.
  - Some formulae aren't binaries (e.g. `antidote` is a sourced zsh script). Don't check them with `command -v`.
- **A shell shortcut:** if it shadows a standard command with a tool whose flags differ (`ls`→eza, `cat`→bat), use `zsh-abbr` in `dot_zshrc`, not `alias`, so pasted commands still get the real tool. The exception is a replacement that accepts the original's flags: `grep`→ugrep and `find`→bfs are plain aliases, using the same flags as Claude Code's Bash tool so human and agent get the same results. New names (`ll`, `la`) can be plain aliases. Shadowing an existing command needs `abbr -S -q -f … >/dev/null` (without `-f` it silently refuses).
- **Telemetry opt-outs:** add them to `.chezmoidata/telemetry.toml` only. Use `env` for env vars, `claude_env` for Claude Code, and `commands` (`tool`/`run`/`check`) for settings stored in a tool's own config. `dotfiles-doctor` verifies all three.
- **A macOS setting:** add a `[[macos.defaults]]` entry to `.chezmoidata/macos.toml` (`domain`, `key`, `value`, optional `restart`). The `defaults` type comes from the TOML value, so write `false`, not `0`. Find the key by diffing `defaults read <domain>` before and after toggling it in System Settings. `dotfiles-doctor` reads each one back. Nothing that needs `sudo` (CI and unattended applies can't answer a password prompt). The one exception, `agent-sleep-guard`, asks only when its root files are missing or changed, and skips that step without a terminal (run `chezmoi apply` in a real terminal, not through an agent).
- **A default app for a file type:** add the extension to a `[[macos.default_apps]]` entry in `macos.toml`. Each entry names the `group` that installs its app and is skipped when that group is off. Check `mdls -name kMDItemContentType <file>` first: an extension whose UTI is `dyn.*` can't be set (`duti` fails with -50). LaunchServices applies changes a few seconds late, so a `dotfiles-doctor` run right after apply may still show the old app.
- **An env var for every shell** (PATH etc.): `dot_zshenv`. Agents run non-interactive shells, so PATH set in `.zshrc` is invisible to them. Login-shell-only setup: `dot_zprofile`. Interactive-only: `dot_zshrc`.

## Before committing

1. `chezmoi apply` (or `chezmoi diff` for a dry run).
2. Run `dotfiles-doctor --json` and expect `"ok": true` once committed and pushed. Each failing field has a hint in `fixes`. `brew.unlisted` is informational, listing packages the user installed by hand, so never add them to a Brewfile unless asked.
3. Commit. The pre-commit hook runs `.githooks/lint` (the same checks as CI) and gitleaks, and you shouldn't bypass it. When adding a lint, put it in `.githooks/lint` so both stay in step.
4. Push. Don't block on CI (the macOS run takes 2–5 min), so check `gh run list -L1` later. CI installs from scratch with every optional group off, requires `dotfiles-doctor` to be clean, resolves every name in the optional Brewfiles, and runs `.githooks/lint`. A red CI means a new Mac would break.

Note: `claude-code@latest` and `chrome-devtools-mcp@latest` float on purpose, so a weekly CI failure may come from a new upstream release rather than a change here.

## Don'ts

- Don't commit `~/.claude.json`, history, tokens, `autoMode`, or anything under `~/.ssh` except public-safe references.
- Don't edit rendered template output in `$HOME`.
- Don't run `claude …` commands in the after-script: on a fresh Mac the first run rewrites `~/.claude/settings.json` and leaves drift. Put them in the before-script.
- Don't add aliases that shadow POSIX tools with flag-incompatible ones (fd, eza, bat).
- Don't pin GitHub Actions by tag. Pin by commit SHA with the tag in a comment (zizmor enforces this), and prefer first-party actions or running the tool directly.
