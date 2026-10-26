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
| `.chezmoidata/packages.toml` | **Only** place optional packages/Brewfile groups are defined |
| `.chezmoidata/telemetry.toml` | **Only** place telemetry opt-outs are defined (env vars, Claude env, per-tool commands + checks) |
| `.chezmoidata/codex.toml` | Public Codex settings; merge only these keys into the private config |
| `.chezmoidata/mcp.toml` | Shared Chrome DevTools MCP command and arguments for both clients |
| `.chezmoidata/agents.toml` | Shared global instructions rendered into Claude CLAUDE.md and Codex AGENTS.md |
| `scripts/codex_config.py` | TOML-preserving Codex config merger, run by uv with a pinned tomlkit dependency |
| `scripts/air/` | Claude Code ACP bridge and Java source for Air's local UI patch; setup merges only its own agent and VM option |
| `scripts/test_agents.py` | Agent group/template, private config, shell PATH and sleep guard regression tests |
| `.chezmoidata/macos.toml` | **Only** place macOS `defaults` and default apps (duti) are defined |
| `packages/Brewfile` | Everything the dotfiles depend on |
| `packages/Brewfile.<group>` | One file per optional group (opt-in, stored as `.install.<group>`). Keep them to essentials only. |
| `uv-tools.txt` | Python CLIs installed with `uv tool install` (e.g. pyrefly) |
| `run_onchange_before_install-packages.sh.tmpl` | brew bundle, tap trust, uv tools, gh/codex telemetry, Claude Code MCP servers, git hooks path |
| `run_onchange_after_macos-defaults.sh.tmpl` | `defaults write` and `duti -s` for each entry in `macos.toml`, then relaunches apps |
| `run_after_agent-sleep-guard.sh.tmpl` | Installs the shared lid-close sleep guard for Claude Code or Codex; removes it only when both groups are off. Root setup requires a real terminal; CI skips it |
| `run_onchange_after_setup.sh.tmpl` | `mise install --locked`, tinty theme |
| `run_after_zz_air.sh.tmpl` | Builds the Air UI agent from Air's bundled JDK and installs the Claude Code bridge when ide and claude are enabled |
| `dot_config/mise/mise.lock` | Exact runtime versions + checksums that `latest`/`lts` resolve to |
| `claude-settings.json` | Source for `~/.claude/settings.json` (see below) |
| `dot_local/bin/` | `claude-sync`, `dotfiles-doctor`, `agent-sleep-guard` |
| `dot_local/agent-bin/` | Shared agent `rm` → Trash. PATH selection uses `$CLAUDECODE` or `DOTFILES_AGENT=codex`, including login-shell reordering |
| `.githooks/lint` | shellcheck (rendered scripts), actionlint, zizmor, ruff and agent regression tests. Run by pre-commit **and** CI |
| `.githooks/pre-commit` | `.githooks/lint`, then gitleaks on staged changes |
| `.github/workflows/test.yml` | Fresh-macOS install test, weekly + on push |

## How edits flow

- **Symlink mode.** Plain files in `$HOME` are symlinks into this repo. Editing `~/.zshrc` edits `dot_zshrc` directly, and you never need `chezmoi re-add`.
- **Templates are copies.** Edit the `.tmpl` in the repo, then run `chezmoi apply`. Never edit the rendered file in `$HOME`, because the next apply overwrites it.
- **`executable_` files are copies too**, not symlinks: `~/.local/bin/dotfiles-doctor`, `~/.local/bin/claude-sync`, `~/.claude/statusline.sh`. Edit them in `dot_local/bin/` / `dot_claude/` and run `chezmoi apply`.
- **Colors.** Never hardcode a hex color or theme name. Add a key to `.chezmoidata/theme.toml` and reference it as `{{ .theme.palette.base0D }}` from a `.tmpl`. Shell-side consumers read `~/.config/zsh/theme.zsh`. Tools that accept ANSI color names (delta's `base16` theme, zsh-patina) should use those so they follow the palette automatically. When changing `theme.scheme`, copy the palette from tinty's scheme yaml, and `dotfiles-doctor` flags any mismatch.
- **Fonts.** Terminal fonts live in `theme.font` in `theme.toml`, and Ghostty is rendered from it. Changing a font means editing `theme.font.families` **and** its `cask` in `packages/Brewfile`. `dotfiles-doctor` fails if they disagree or Ghostty can't find the family name, because Ghostty otherwise silently falls back to a default font.
- **Claude Code settings.** `dot_claude/modify_settings.json.tmpl` builds `~/.claude/settings.json` from `claude-settings.json`, adds `telemetry.claude_env`, and keeps the machine's local `autoMode` block, which holds private repo names and never goes in git. `claude-sync` does the reverse and strips both. After changing settings via `/config`, run `claude-sync`. Never add telemetry keys to `claude-settings.json` directly.
- **Codex settings.** `private_dot_codex/modify_private_config.toml.tmpl` passes public template data to `scripts/codex_config.py`. The merger changes only `.codex`, `telemetry.codex` and the shared Canary MCP transport, preserving comments, model/provider choices, trust and private MCP credentials. Never add the live config, auth, sessions, rules or plugin state to git. Manage public defaults in `.chezmoidata/codex.toml`; telemetry belongs only in telemetry.toml. `uv run --script` provisions its pinned TOML dependency before mise's after-script. The modifier must receive rendered data, not read the user's global chezmoi config, so overrides and isolated destinations work.
- **Air bridge and UI.** The ide group installs `jetbrains-air`; when claude is also enabled, `scripts/air/setup.py` copies `bridge.py`, builds the Java agent with Air's bundled JDK, merges only the `Claude Code` entry in Air's private `acp.json`, and adds one `-javaagent` line to the user's `Air.vmoptions`. Never commit Air's live `acp.json`, `mcp.json`, workspace database, session files, or usage data. Java UI patches depend on Air internals and need checking after an Air update. Keep the model label dynamic in `DefaultName`, and preserve other agents and VM options.
- **Global agent instructions.** `.chezmoidata/agents.toml` is the shared source for both global instruction files. Their modify templates use `.chezmoitemplates/agent-instructions` to replace only the marked managed block, preserving private local guidance outside it. Updating agents.toml updates both clients on apply. The `.tmpl` suffix also avoids chezmoi's automatic exclusion of plain AGENTS.md/CLAUDE.md sources. Keep common instructions, MCP transports and telemetry in shared data; client-specific model/UI settings remain separate.
- **No nested chezmoi in apply.** Before-scripts and modify scripts hold the persistent state lock. Pass rendered data into modifiers. The before-script exports `DOTFILES_SOURCE_DIR`, and telemetry-only Codex merging reads telemetry.toml directly, so neither needs to launch another chezmoi process.
- **Codex sleep detection.** `features.prevent_idle_sleep` creates a native macOS `PreventUserIdleSystemSleep` assertion. The guard checks assertions owned by `codex`, plus Claude/Codex caffeinate children; a running idle client alone does not count. `agent-sleep-guard --check` is read-only and requires no sudo. Keep battery/grace/boot recovery behavior shared.
- **Git merges.** `~/.config/git/attributes` routes supported languages to mergiraf (driver in `dot_gitconfig`); it falls back to normal zdiff3 markers when it can't solve a conflict. Regenerate the attributes with `mergiraf languages --gitattributes` after upgrading mergiraf.
- **Machine-specific values** go in templates gated by `stat` or data (see `dot_config/git/local.tmpl`, where commit signing is only enabled if the key exists). Never hardcode `/Users/<name>`. Use `$HOME`, `~`, or `{{ .chezmoi.homeDir }}`.
- **Machine-local shell config and secrets** go in `~/.zshrc.local` / `~/.zshenv.local` (sourced if present, never in git), not in `dot_zshrc`. Installers that append lines to `~/.zprofile` etc. are editing the repo through the symlink, so move those lines there too.
- **Runtime versions.** `mise.lock` freezes what `latest`/`lts` mean, and the after-script installs with `--locked`, so every Mac gets the same versions. `minimum_release_age = "7d"` keeps brand-new releases out. To move forward: `mise lock -g --bump && mise install`, then commit `mise.lock`. After adding a tool to `config.toml`, run `mise lock -g` or `--locked` fails. `~/.config/mise/mise.lock` is a symlink and mise writes through it, so it stays in sync.

## Adding things

- **Git identity.** Init prompts once for `data.git.name` and `data.git.email`, defaulting to the existing global Git config including its includes. Keep these values in local chezmoi config; `dot_config/git/local.tmpl` renders them alongside conditional signing settings. Never put a personal identity in `dot_gitconfig`.

- **Removing or renaming a config file:** add the old target (relative to `$HOME`) to `.chezmoiremove`, or `$HOME` keeps a dangling symlink into the repo. `dotfiles-doctor` reports those as `dangling_symlinks`.
- **A newer chezmoi feature:** bump `.chezmoiversion` to the release that added it.
- **An empty file** (e.g. `~/.hushlogin`): chezmoi skips empty sources unless they're named `empty_dot_…`.
- **A config file:** `chezmoi add <path>`. Then check the source name has no `private_` or `executable_` prefix it doesn't need, because those attributes make chezmoi **copy** instead of symlink. `chmod 644` the target if needed.
- **A package:** add it to `packages/Brewfile` if a dotfile depends on it, otherwise to the matching optional `packages/Brewfile.*` — only if it's genuinely essential. Packages installed by hand and not listed anywhere are fine; `dotfiles-doctor` lists them as informational.
  - A new optional group needs two things: a `[[groups]]` entry in `.chezmoidata/packages.toml` and a `packages/Brewfile.<name>`. Nothing else, because the prompt, install script, doctor and CI all loop over that list. Then run `chezmoi init` to answer the new prompt. Descriptions must not contain commas.
  - A group also gates its config in `.chezmoiignore`, scripts and doctor. Use `index .install "<name>"`, not `.install.<name>`: old machines may have no key. `claude` gates `~/.claude` and `claude-sync`; `codex` gates managed files under `~/.codex`. Either enables `agent-bin` and `agent-sleep-guard`; remove shared guard setup only when both are off. Canary MCP is configured for each enabled agent when `canary` is on. Turning Canary off disables only the matching managed Codex transport, preserving credentials. Default apps need `ide`.
  - `.chezmoi.toml.tmpl` can't see `.chezmoidata` (it isn't loaded at init yet), so it reads `packages.toml` with `include … | fromToml`.
  - CI installs separately with `claude = true` or `codex = true`, then `chezmoi init --promptDefaults`. Regression tests render both/one/neither with Canary on/off and missing group keys. Don't use `--promptBool`: it matches by prompt text and splits on commas.
  - Formulae from third-party taps need an explicit `brew trust --formula|--cask <full/name>` line in the install script (Homebrew 7 refuses untrusted taps). Trust the single formula, not the whole tap.
  - The install script uses `brew bundle --no-upgrade`. Without it, every packages/Brewfile change also upgrades every outdated package (minutes of big cask downloads). Upgrades are a separate, deliberate `brew upgrade`.
  - Python CLIs not in Homebrew go in `uv-tools.txt`. The install script installs them and `dotfiles-doctor` checks them.
  - Some formulae aren't binaries (e.g. `antidote` is a sourced zsh script). Don't check them with `command -v`.
- **A shell shortcut:** if it shadows a standard command with a tool whose flags differ (`ls`→eza, `cat`→bat), use `zsh-abbr` in `dot_zshrc`, not `alias`, so pasted commands still get the real tool. The exception is a replacement that accepts the original's flags: `grep`→ugrep and `find`→bfs are plain aliases, using the same flags as Claude Code's Bash tool so human and agent get the same results. New names (`ll`, `la`) can be plain aliases. Shadowing an existing command needs `abbr -S -q -f … >/dev/null` (without `-f` it silently refuses).
- **Telemetry opt-outs:** add them to `.chezmoidata/telemetry.toml` only. Use `env`, `claude_env`, `codex` (TOML keys), and `commands` (`tool`/`run`/`check`). Codex telemetry also applies to an already installed CLI when its optional group is off; the telemetry-only merge must not enable agent features. Never append TOML with grep: an existing table may have a different value.
- **A macOS setting:** add a `[[macos.defaults]]` entry to `.chezmoidata/macos.toml` (`domain`, `key`, `value`, optional `restart`). The `defaults` type comes from the TOML value, so write `false`, not `0`. Find the key by diffing `defaults read <domain>` before and after toggling it in System Settings. `dotfiles-doctor` reads each one back. Nothing that needs `sudo` (CI and unattended applies can't answer a password prompt). The one exception, `agent-sleep-guard`, asks only when its root files are missing or changed, and skips that step without a terminal (run `chezmoi apply` in a real terminal, not through an agent).
- **A default app for a file type:** add the extension to a `[[macos.default_apps]]` entry in `macos.toml`. Each entry names the `group` that installs its app and is skipped when that group is off. Check `mdls -name kMDItemContentType <file>` first: an extension whose UTI is `dyn.*` can't be set (`duti` fails with -50). LaunchServices applies changes a few seconds late, so a `dotfiles-doctor` run right after apply may still show the old app.
- **An env var for every shell** (PATH etc.): `dot_zshenv`. Agents run non-interactive shells, so PATH set in `.zshrc` is invisible to them. Login-shell-only setup: `dot_zprofile`. Interactive-only: `dot_zshrc`.

## Before committing

1. `chezmoi apply` (or `chezmoi diff` for a dry run).
2. Run `dotfiles-doctor --json` and expect `"ok": true` once committed and pushed. Each failing field has a hint in `fixes`. `brew.unlisted` is informational, listing packages the user installed by hand, so never add them to a packages/Brewfile unless asked.
3. Commit. The pre-commit hook runs `.githooks/lint` (the same checks as CI) and gitleaks, and you shouldn't bypass it. When adding a lint, put it in `.githooks/lint` so both stay in step.
4. Push. Don't block on CI (the macOS run takes 2–5 min), so check `gh run list -L1` later. CI installs each agent independently, checks doctor, resolves optional packages/Brewfile names, and runs `.githooks/lint`. A red CI means a new Mac would break.

Note: `claude-code@latest` and `chrome-devtools-mcp@latest` float on purpose, so a weekly CI failure may come from a new upstream release rather than a change here.

## Don'ts

- Don't commit `~/.claude.json`, history, tokens, `autoMode`, or anything under `~/.ssh` except public-safe references.
- Don't edit rendered template output in `$HOME`.
- Don't run `claude …` commands in the after-script: on a fresh Mac the first run rewrites `~/.claude/settings.json` and leaves drift. Put them in the before-script.
- Don't add aliases that shadow POSIX tools with flag-incompatible ones (fd, eza, bat).
- Don't pin GitHub Actions by tag. Pin by commit SHA with the tag in a comment (zizmor enforces this), and prefer first-party actions or running the tool directly.
