#!/bin/sh
# Claude Code statusline — matches the tinty base16-snazzy palette
input=$(cat)

model=$(echo "$input" | jq -r '.model.display_name // "?"' | tr '[:upper:]' '[:lower:]' | tr ' ' '-')
dir=$(echo "$input" | jq -r '.workspace.current_dir // .cwd // "?"')
ctx_pct=$(echo "$input" | jq -r '.context_window.used_percentage // empty')
five_h=$(echo "$input" | jq -r '.rate_limits.five_hour.used_percentage // empty')
seven_d=$(echo "$input" | jq -r '.rate_limits.seven_day.used_percentage // empty')

dirname=$(basename "$dir")
branch=$(git -C "$dir" branch --show-current 2>/dev/null)

# base16-snazzy truecolor
cyan='\033[38;2;154;237;254m'
green='\033[38;2;90;247;142m'
yellow='\033[38;2;243;249;157m'
blue='\033[38;2;87;199;255m'
magenta='\033[38;2;255;106;193m'
red='\033[38;2;255;92;87m'
muted='\033[38;2;120;120;126m'
reset='\033[0m'

BAR_WIDTH=10

# builds "label [bar] pct%" in $bar_out. $1=label $2=pct (numeric or empty)
render_bar() {
  label=$1
  pct=$2
  if [ -z "$pct" ]; then
    bar_out=$(printf '%b%s --%b' "$muted" "$label" "$reset")
    return
  fi
  pct_int=$(printf '%.0f' "$pct")
  if [ "$pct_int" -lt 50 ]; then
    color="$green"
  elif [ "$pct_int" -lt 80 ]; then
    color="$yellow"
  else
    color="$red"
  fi
  filled=$(((pct_int * BAR_WIDTH + 50) / 100))
  [ "$filled" -gt "$BAR_WIDTH" ] && filled=$BAR_WIDTH
  [ "$filled" -lt 0 ] && filled=0
  empty=$((BAR_WIDTH - filled))
  filled_str=""
  [ "$filled" -gt 0 ] && filled_str=$(printf '%0.sX' $(seq 1 "$filled") 2>/dev/null | tr 'X' '█')
  empty_str=""
  [ "$empty" -gt 0 ] && empty_str=$(printf '%0.sX' $(seq 1 "$empty") 2>/dev/null | tr 'X' '░')
  bar_out=$(printf '%b%s%b %b%s%b%b%s%b %b%s%%%b' \
    "$muted" "$label" "$reset" \
    "$color" "$filled_str" "$reset" "$muted" "$empty_str" "$reset" \
    "$color" "$pct_int" "$reset")
}

# line 1: model @ dir [branch]
line1="${cyan}${model}${reset} ${muted}@${reset} ${blue}${dirname}${reset}"
if [ -n "$branch" ]; then
  line1="${line1} ${muted}on${reset} ${magenta}${branch}${reset}"
fi

# line 2: ctx / 5h / 7d — each segment only appears once its data exists
line2=""
if [ -n "$ctx_pct" ]; then
  render_bar ctx "$ctx_pct"
  line2="$bar_out"
fi
if [ -n "$five_h" ]; then
  render_bar 5h "$five_h"
  [ -n "$line2" ] && line2="${line2}    ${bar_out}" || line2="$bar_out"
fi
if [ -n "$seven_d" ]; then
  render_bar 7d "$seven_d"
  [ -n "$line2" ] && line2="${line2}    ${bar_out}" || line2="$bar_out"
fi

if [ -n "$line2" ]; then
  printf "%b\n%b\n" "$line1" "$line2"
else
  printf "%b\n" "$line1"
fi
