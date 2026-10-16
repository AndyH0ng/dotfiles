# dotfiles

[chezmoi](https://chezmoi.io)로 관리하는 macOS 터미널 설정.

## 설치

```sh
sh -c "$(curl -fsLS get.chezmoi.io)" -- init --apply AndyH0ng
```

필요하면 Homebrew를 설치하고, [Brewfile](Brewfile)로 `brew bundle`을 실행한 뒤 tinty 테마를 적용한다. `chezmoi init` 때 선택 그룹마다 설치 여부를 한 번씩 묻는다: [Brewfile.dev](Brewfile.dev) (IDE, OrbStack, Chrome Canary, Figma), [Brewfile.apps](Brewfile.apps) (AdGuard, Notion, LaunchOS), [Brewfile.fonts](Brewfile.fonts) (Pretendard, SF).

## 구성

- **쉘**: zsh + [antidote](https://github.com/mattmc3/antidote) 플러그인(fzf-tab, autosuggestions, completions, zsh-abbr), [starship](https://starship.rs) 프롬프트, [zsh-patina](https://github.com/michel-kraemer/zsh-patina) 구문 강조
- **터미널**: 커스텀 아이콘을 쓴 [Ghostty](https://ghostty.org), IBM Plex Mono + D2Coding + Nerd Font 심볼
- **테마**: base16-snazzy. [`.chezmoidata/theme.toml`](.chezmoidata/theme.toml) 한 곳에서 정의하고 Ghostty, starship, Claude Code 상태바, [tinty](https://github.com/tinted-theming/tinty)(쉘 + fzf), `vivid`(`LS_COLORS`)에 반영
- **도구**: mise(언어 런타임), zoxide, fzf, atuin(인라인, 10줄), eza, bat, ripgrep, fd, xh, fastfetch
- **Python**: uv, ruff, [pyrefly](https://pyrefly.org)(타입 체커, [uv-tools.txt](uv-tools.txt)로 설치)
- **Git**: 최신 기본값(histogram diff, zdiff3, rerere, autoSetupRemote), pager로 [delta](https://github.com/dandavison/delta) 사용(lazygit 포함), 서명 키가 있을 때만 SSH 커밋 서명
- **Claude Code**: `settings.json`(플러그인, 테마, 상태바)과 base16-snazzy [상태바](dot_claude/executable_statusline.sh.tmpl), 실행 중인 Chrome Canary에 붙는 chrome-devtools MCP(`chrome://inspect/#remote-debugging`에서 원격 디버깅을 켜야 함)
- **프라이버시**: Homebrew, gh, Claude Code, Codex, CodeRabbit, 주요 JS 도구의 텔레메트리 끔(`.zshenv`의 `DO_NOT_TRACK` 등)

## 수정하기

chezmoi는 심링크 모드로 동작한다. `~/.zshrc` 같은 일반 파일은 이 저장소를 가리키는 심링크라서, 그 자리에서 고치고 `~/dotfiles`에서 커밋하면 된다.

`.tmpl`로 끝나는 파일은 렌더링된 복사본이다. `chezmoi edit <대상>`이나 저장소에서 고친 뒤 `chezmoi apply`를 실행한다.

- **테마**: `.chezmoidata/theme.toml`을 고치고 `chezmoi apply`.
- **Claude Code 설정**: `~/.claude/settings.json`은 [`claude-settings.json`](claude-settings.json)으로부터 `modify_` 스크립트가 생성한다. 이 스크립트는 머신별 `autoMode` 블록을 git 밖에 둔다. `/config`로 설정을 바꿨다면 `claude-sync`로 저장소에 다시 반영한다.

## Chrome Canary 연결 (chrome-devtools MCP)

Claude Code의 chrome-devtools MCP는 별도 창을 띄우지 않고, **이미 켜져 있는 Chrome Canary(로그인된 프로필)에 붙는다.** 설치 스크립트가 `claude mcp add`로 아래 설정을 등록한다.

```sh
bunx chrome-devtools-mcp@latest --autoConnect --channel canary --no-usage-statistics
```

처음 한 번:

1. Canary를 켜고 `chrome://inspect/#remote-debugging`에서 원격 디버깅을 켠다.
2. Claude Code를 재시작한다.
3. 첫 연결 때 Canary에 뜨는 허용 창에서 허용한다.

주의할 점:

- Canary가 미리 켜져 있어야 한다. MCP가 직접 실행하지 않는다.
- 에이전트가 로그인된 계정 그대로 페이지를 조작할 수 있다. 원격 디버깅은 필요할 때만 켜 두는 게 좋다.
- 같은 기능의 `chrome-devtools-mcp` 플러그인은 일반 Chrome용이라 꺼 뒀다.

## 유지보수

- `dotfiles-doctor`(에이전트용은 `dotfiles-doctor --json`)는 어긋난 부분을 보여준다: 저장소와 다른 파일, 설치 안 된 패키지·uv 도구, 테마 팔레트 불일치, Claude 설정 불일치, 커밋·push 안 한 변경. 직접 설치했지만 Brewfile에 없는 패키지는 참고용으로만 표시한다.
- pre-commit hook이 스테이징된 변경에 `gitleaks`를 돌린다. 설치 스크립트가 `core.hooksPath`로 켜 둔다.
- CI는 새 macOS 러너에 전부 설치한 뒤 zsh 시작, `dotfiles-doctor`, 선택 Brewfile 패키지 이름, 셸 스크립트(shellcheck)를 검사한다. push할 때와 매주 실행된다.
- [AGENTS.md](AGENTS.md)(`CLAUDE.md`와 동일)에 AI 에이전트를 위한 저장소 규칙과 함정을 정리해 뒀다.
