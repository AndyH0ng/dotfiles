# dotfiles

[chezmoi](https://chezmoi.io)로 관리하는 macOS 터미널 설정.

## 설치

```sh
xcode-select --install   # 먼저: 새 Mac의 git은 Command Line Tools 설치 창만 띄우는 껍데기다
sh -c "$(curl -fsLS get.chezmoi.io)" -- init --apply --source ~/dotfiles AndyH0ng
```

필요하면 Homebrew를 설치하고, [Brewfile](Brewfile)로 `brew bundle`을 실행한 뒤 tinty 테마를 적용한다. `chezmoi init` 때 선택 그룹마다 설치 여부를 한 번씩 묻는다. 그룹을 켜면 앱과 그에 딸린 설정이 같이 들어간다.

- [Brewfile.claude](Brewfile.claude): Claude Code와 설정·상태바, 에이전트 `rm` → 휴지통, 작업 중 잠자기 방지
- [Brewfile.codex](Brewfile.codex): Codex CLI와 설정·전역 지침, 에이전트 `rm` → 휴지통, 작업 중 잠자기 방지. Claude와 독립적으로 선택 가능
- [Brewfile.ide](Brewfile.ide): WebStorm, IntelliJ, 코드 파일 기본 앱을 WebStorm으로
- [Brewfile.dev](Brewfile.dev): OrbStack
- [Brewfile.canary](Brewfile.canary): Chrome Canary, claude 또는 codex도 켜면 각 클라이언트에 chrome-devtools MCP 등록
- [Brewfile.apps](Brewfile.apps): AdGuard, Notion, LaunchOS, Figma
- [Brewfile.fonts](Brewfile.fonts): Pretendard, SF

## 구성

- **쉘**: zsh + [antidote](https://github.com/mattmc3/antidote) 플러그인(fzf-tab, autosuggestions, completions, zsh-abbr), [starship](https://starship.rs) 프롬프트, [zsh-patina](https://github.com/michel-kraemer/zsh-patina) 구문 강조
- **터미널**: 커스텀 아이콘을 쓴 [Ghostty](https://ghostty.org), IBM Plex Mono + D2Coding + Nerd Font 심볼
- **테마**: base16-snazzy. [`.chezmoidata/theme.toml`](.chezmoidata/theme.toml) 한 곳에서 정의하고 Ghostty, starship, Claude Code 상태바, [tinty](https://github.com/tinted-theming/tinty)(쉘 + fzf), `vivid`(`LS_COLORS`)에 반영
- **도구**: mise(언어 런타임), zoxide, fzf, atuin(인라인, 10줄), eza, bat, fd, xh, fastfetch, `grep`/`find`는 옵션 호환인 [ugrep](https://ugrep.com)/[bfs](https://github.com/tavianator/bfs)로 대체(Claude Code와 같은 엔진), claude 그룹이면 AI 에이전트의 `rm`은 휴지통으로 이동(사람은 그대로 삭제, 휴지통은 30일 뒤 자동 비움), Claude Code가 작업 중이면 뚜껑을 닫아도 잠들지 않음(처음 한 번 터미널에서 `chezmoi apply`로 sudo 승인)
- **Python**: uv, ruff, [pyrefly](https://pyrefly.org)(타입 체커, [uv-tools.txt](uv-tools.txt)로 설치)
- **Git**: 최신 기본값(histogram diff, zdiff3, rerere, autoSetupRemote), pager로 [delta](https://github.com/dandavison/delta) 사용(lazygit 포함), [mergiraf](https://mergiraf.org)로 구문 기반 merge(TS·JSON·YAML 등), 서명 키가 있을 때만 SSH 커밋 서명
- **Claude Code**: `settings.json`(플러그인, 테마, 상태바)과 base16-snazzy [상태바](dot_claude/executable_statusline.sh.tmpl), canary 그룹이면 실행 중인 Chrome Canary에 붙는 chrome-devtools MCP(`chrome://inspect/#remote-debugging`에서 원격 디버깅을 켜야 함)
- **Codex**: CLI와 전역 `AGENTS.md`, [`codex.toml`](.chezmoidata/codex.toml)의 관리 설정을 `~/.codex/config.toml`에 병합한다. 모델 선택·프로젝트 신뢰·개인 MCP·인증 설정은 로컬에 유지한다. 비로그인 셸에서도 Homebrew와 mise 런타임을 사용할 수 있다
- **macOS 설정**: 키 반복, 텍스트 자동 치환 끄기, Finder 경로 막대, 네트워크·USB에 `.DS_Store` 안 만들기, ide 그룹이면 코드 파일 기본 앱을 WebStorm으로(duti) 등. 목록은 [`.chezmoidata/macos.toml`](.chezmoidata/macos.toml) 한 곳에 있다
- **프라이버시**: Homebrew, gh, Claude Code, Codex, CodeRabbit, 주요 JS 도구의 텔레메트리 끔. 목록은 [`.chezmoidata/telemetry.toml`](.chezmoidata/telemetry.toml) 한 곳에 있다

## 수정하기

chezmoi는 심링크 모드로 동작한다. `~/.zshrc` 같은 일반 파일은 이 저장소를 가리키는 심링크라서, 그 자리에서 고치고 `~/dotfiles`에서 커밋하면 된다.

`.tmpl`로 끝나는 파일은 렌더링된 복사본이다. `chezmoi edit <대상>`이나 저장소에서 고친 뒤 `chezmoi apply`를 실행한다.

- **테마**: `.chezmoidata/theme.toml`을 고치고 `chezmoi apply`.
- **언어 런타임**: [`mise.lock`](dot_config/mise/mise.lock)이 `latest`/`lts`가 가리키는 정확한 버전과 체크섬을 고정하고, 설치는 `mise install --locked`로 한다. 나온 지 7일이 안 된 릴리스는 쓰지 않는다. 올릴 때는 `mise lock -g --bump && mise install` 후 `mise.lock`을 커밋한다.
- **머신 전용 설정·비밀값**: `~/.zshrc.local`, `~/.zshenv.local`에 둔다(있으면 자동으로 source하며 git에는 들어가지 않는다). `~/.zshrc`는 공개 저장소로 연결된 심링크다.
- **Claude Code 설정**: `~/.claude/settings.json`은 [`claude-settings.json`](claude-settings.json)으로부터 `modify_` 스크립트가 생성한다. 이 스크립트는 머신별 `autoMode` 블록을 git 밖에 둔다. `/config`로 설정을 바꿨다면 `claude-sync`로 저장소에 다시 반영한다.
- **Codex 설정**: 공개 기본값은 [`.chezmoidata/codex.toml`](.chezmoidata/codex.toml), 텔레메트리는 [`telemetry.toml`](.chezmoidata/telemetry.toml), Canary MCP 명령은 [`mcp.toml`](.chezmoidata/mcp.toml)에 있다. `chezmoi apply`는 이 키만 갱신하고 기존 개인 설정과 주석은 보존한다. 기존 Mac에서 Codex 지원을 켜려면 `chezmoi init`에서 codex를 선택한 뒤 `chezmoi apply`한다. 최초 로그인은 직접 `codex login`으로 한다.

Claude 또는 Codex 그룹을 켜면 에이전트 셸의 `rm`이 휴지통을 사용한다. 전역 작업 지침은 [`agents.toml`](.chezmoidata/agents.toml) 한 곳에서 정의하고, `chezmoi apply`가 양쪽 클라이언트의 관리 블록을 갱신한다. 블록 밖의 기존 개인 지침은 보존한다. 잠자기 guard도 두 클라이언트가 공유한다. Codex는 실험 기능인 `prevent_idle_sleep`을 켜고, guard가 실제 macOS 잠자기 방지 요청을 감지한다. guard의 시스템 설치는 처음 한 번 실제 터미널에서 `chezmoi apply`로 sudo 승인이 필요하다.

## Chrome Canary 연결 (chrome-devtools MCP)

Claude Code와 Codex의 chrome-devtools MCP는 **이미 켜져 있는 Chrome Canary(로그인된 프로필)에 붙는다.** 두 클라이언트는 [같은 명령](.chezmoidata/mcp.toml)을 사용한다. Claude는 설치 스크립트가 등록하고, Codex는 설정 병합으로 등록한다.

```sh
bunx chrome-devtools-mcp@latest --autoConnect --channel canary --no-usage-statistics
```

처음 한 번:

1. Canary를 켜고 `chrome://inspect/#remote-debugging`에서 원격 디버깅을 켠다.
2. 사용하는 Claude Code 또는 Codex를 재시작한다.
3. 첫 연결 때 Canary에 뜨는 허용 창에서 허용한다.

주의할 점:

- Canary가 미리 켜져 있어야 한다. MCP가 직접 실행하지 않는다.
- 에이전트가 로그인된 계정 그대로 페이지를 조작할 수 있다. 원격 디버깅은 필요할 때만 켜 두는 게 좋다.
- 같은 기능의 `chrome-devtools-mcp` 플러그인은 일반 Chrome용이라 꺼 뒀다.

## 유지보수

- `dotfiles-doctor`(에이전트용은 `dotfiles-doctor --json`)는 어긋난 부분을 보여준다: 저장소와 다른 파일, 설치 안 된 패키지·uv 도구, 테마 팔레트 불일치, Claude 설정 불일치, 커밋·push 안 한 변경. 직접 설치했지만 Brewfile에 없는 패키지는 참고용으로만 표시한다.
- pre-commit hook이 `.githooks/lint`(shellcheck, actionlint, zizmor — CI와 같은 검사)와 스테이징된 변경에 대한 `gitleaks`를 돌린다. 설치 스크립트가 `core.hooksPath`로 켜 둔다.
- CI는 Claude만 켠 Mac과 Codex만 켠 Mac에 각각 설치한 뒤 zsh 시작, `dotfiles-doctor`, 선택 Brewfile 패키지 이름, `.githooks/lint`를 검사한다. 두 그룹의 네 가지 조합과 Canary on/off, 개인 Codex 설정 보존도 검증한다. push할 때와 매주 실행된다.
- [AGENTS.md](AGENTS.md)(`CLAUDE.md`와 동일)에 AI 에이전트를 위한 저장소 규칙과 함정을 정리해 뒀다.
