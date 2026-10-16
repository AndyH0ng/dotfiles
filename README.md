# dotfiles

macOS terminal setup, managed with [chezmoi](https://chezmoi.io).

## Install

```sh
sh -c "$(curl -fsLS get.chezmoi.io)" -- init --apply AndyH0ng
```

This installs Homebrew if needed, runs `brew bundle` with the [Brewfile](Brewfile), and applies the tinty theme.

## What's inside

- **Shell**: zsh with [antidote](https://github.com/mattmc3/antidote) plugins (fzf-tab, autosuggestions, completions, zsh-abbr), [starship](https://starship.rs) prompt, [zsh-patina](https://github.com/michel-kraemer/zsh-patina) syntax highlighting
- **Terminal**: [Ghostty](https://ghostty.org) with a custom icon, IBM Plex Mono + D2Coding + Nerd Font symbols
- **Theme**: base16-snazzy, defined once in [`.chezmoidata/theme.toml`](.chezmoidata/theme.toml) and rendered into Ghostty, starship, the Claude Code statusline, [tinty](https://github.com/tinted-theming/tinty) (shell + fzf) and `vivid` (`LS_COLORS`)
- **Tools**: mise (language runtimes), zoxide, fzf, atuin, eza, bat, ripgrep, fd, xh, fastfetch
- **Python**: uv, ruff, ty
- **Git**: modern defaults (histogram diff, zdiff3, rerere, autoSetupRemote), [delta](https://github.com/dandavison/delta) as pager (also in lazygit), SSH commit signing enabled only when the key exists
- **Claude Code**: `settings.json` (plugins, theme, statusline) and a base16-snazzy [statusline](dot_claude/executable_statusline.sh.tmpl)

- **Privacy**: telemetry off for Homebrew, gh, Claude Code, Codex, CodeRabbit and common JS tooling (`DO_NOT_TRACK` etc. in `.zshenv`)

## Editing

chezmoi runs in symlink mode: plain files like `~/.zshrc` are symlinks into this repo, so edit them in place and commit from `~/dotfiles`.

Files ending in `.tmpl` are rendered copies. Edit them with `chezmoi edit <target>` or in the repo, then run `chezmoi apply`.

- **Theme**: edit `.chezmoidata/theme.toml`, then run `chezmoi apply`.
- **Claude Code settings**: `~/.claude/settings.json` is generated from [`claude-settings.json`](claude-settings.json) by a `modify_` script that keeps each machine's `autoMode` block out of git. After changing settings via `/config`, run `claude-sync` to copy them back into the repo.

## Maintenance

- `dotfiles-doctor` reports drift: files that differ from the repo, Brewfile packages missing or unlisted, Claude settings out of sync, and uncommitted or unpushed changes.
- A pre-commit hook runs `gitleaks` on staged changes. The install script enables it via `core.hooksPath`.
- CI installs everything on a fresh macOS runner and checks that zsh starts cleanly.
