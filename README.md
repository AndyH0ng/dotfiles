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
- **Theme**: base16-snazzy via [tinty](https://github.com/tinted-theming/tinty) (shell + fzf), `vivid` for `LS_COLORS`
- **Tools**: mise, zoxide, fzf, atuin, eza, bat, ripgrep, fd, fastfetch
