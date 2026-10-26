# Loaded by antidote after completion paths and before widget plugins.
autoload -Uz compinit
# Rebuild immediately when completion paths or the plugin list changes.
_comp_cache="${XDG_CACHE_HOME:-$HOME/.cache}/zsh/completion-paths"
_comp_paths="${(F)fpath}"
_stale=(~/.zcompdump(N.mh+24))
if (( $#_stale )) || [[ ! -s ~/.zcompdump || ! -r $_comp_cache ||
    "$(<"$_comp_cache")" != "$_comp_paths" || ~/.zsh_plugins.txt -nt ~/.zcompdump ]]; then
  compinit
  zcompile ~/.zcompdump
  mkdir -p "${_comp_cache:h}"
  print -r -- "$_comp_paths" > "$_comp_cache"
else
  compinit -C
fi
unset _stale _comp_cache _comp_paths
