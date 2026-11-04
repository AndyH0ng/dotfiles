# dotfiles

[chezmoi](https://chezmoi.io)로 관리하는 Apple Silicon macOS 개발 환경.

## 설치

```sh
xcode-select --install
sh -c "$(curl -fsLS get.chezmoi.io)" -- init --apply --source ~/dotfiles AndyH0ng
```

`chezmoi init`이 선택 그룹과 Git 이름·이메일을 묻는다. Homebrew 패키지, mise 런타임, macOS 설정, Caps Lock 매핑이 바뀌므로 기존 설정은 먼저 백업한다. fonts 그룹을 켤 거면 먼저 `gh auth login`을 한다. claude·codex 그룹의 잠자기 방지는 처음 한 번 실제 터미널에서 `chezmoi apply`로 sudo 승인이 필요하다.

## 선택 그룹

| 그룹 | 설치 | 비고 |
|---|---|---|
| [claude](packages/Brewfile.claude) | Claude Code, 설정·상태바 | 에이전트 `rm` → 휴지통, 작업 중 잠자기 방지 |
| [codex](packages/Brewfile.codex) | Codex CLI, 설정·전역 지침 | claude와 따로 선택 |
| [ide](packages/Brewfile.ide) | VS Code, 테마·아이콘·Custom UI Style | 코드 파일 기본 앱을 VS Code로 |
| [dev](packages/Brewfile.dev) | OrbStack | |
| [canary](packages/Brewfile.canary) | Chrome Canary | chrome-devtools MCP 등록 |
| [safari](packages/Brewfile.safari) | 없음 (Safari 27 내장) | Safari MCP 등록 |
| [apps](packages/Brewfile.apps) | wBlock, Notion, LaunchOS, Figma | |
| [fonts](packages/Brewfile.fonts) | SF 폰트, LigaSFMono Nerd Font | 비공개 tap, `gh` 로그인 필요 |

선택을 바꾸려면 `chezmoi init --prompt` 후 `chezmoi apply`. 그룹을 꺼도 설치된 패키지는 지우지 않는다.

## 구성

| 영역 | 구성 |
|---|---|
| 쉘 | zsh + [antidote](https://github.com/mattmc3/antidote)(fzf-tab, autosuggestions, completions, zsh-abbr), [starship](https://starship.rs), [zsh-patina](https://github.com/michel-kraemer/zsh-patina) |
| 터미널 | [Ghostty](https://ghostty.org), LigaSFMono Nerd Font + Pretendard, Light, 합자 켬 |
| 테마 | base16-snazzy. [theme.toml](.chezmoidata/theme.toml) 한 곳에서 Ghostty·starship·Claude 상태바·[tinty](https://github.com/tinted-theming/tinty)·vivid·VS Code에 적용 |
| 도구 | mise, zoxide, fzf, atuin, eza, bat, fd, xh, fastfetch, `grep`/`find` → [ugrep](https://ugrep.com)/[bfs](https://github.com/tavianator/bfs) |
| Python | uv, ruff, [pyrefly](https://pyrefly.org) |
| Git | histogram diff, zdiff3, rerere, [delta](https://github.com/dandavison/delta), [mergiraf](https://mergiraf.org), 키가 있으면 SSH 서명 |
| macOS | 키보드·Finder 설정, 기본 앱. [macos.toml](.chezmoidata/macos.toml) |
| 텔레메트리 | Homebrew·gh·에이전트·JS 도구 끔. [telemetry.toml](.chezmoidata/telemetry.toml) |

## 수정

일반 파일(`~/.zshrc` 등)은 이 저장소로 가는 심링크라 그 자리에서 고치고 커밋한다. `.tmpl` 파일은 복사본이라 저장소에서 고친 뒤 `chezmoi apply`한다.

| 항목 | 위치 | 참고 |
|---|---|---|
| 테마·폰트 | [theme.toml](.chezmoidata/theme.toml) | |
| 언어 런타임 | [mise](dot_config/mise/config.toml) · [lock](dot_config/mise/mise.lock) | 올리기: `mise lock -g --bump && mise install` 후 lock 커밋 |
| 머신 전용 설정·비밀값 | `~/.zshrc.local`, `~/.zshenv.local` | git 밖, 자동 source |
| Ghostty 머신 전용 | `~/.config/ghostty/config.local` | |
| Claude Code | [claude-settings.json](claude-settings.json) | `/config`로 바꿨으면 `claude-sync` |
| Codex | [codex.toml](.chezmoidata/codex.toml) | 개인 설정은 보존, 로그인은 `codex login` |
| MCP | [mcp.toml](.chezmoidata/mcp.toml) | |
| VS Code | [settings](Library/Application%20Support/Code/User/settings.json.tmpl) · [CSS/JS](dot_config/vscode) | CSS·JS 변경 후 VS Code에서 `Custom UI Style: Reload` |
| 에이전트 전역 지침 | [agents.toml](.chezmoidata/agents.toml) | 관리 블록 밖 개인 지침은 보존 |

## 한영 전환

지연없이 Caps Lock으로 입력 소스를 바로 전환한다.

```sh
hangul-capslock status
hangul-capslock uninstall
```

## 업데이트

```sh
chezmoi git -- pull --ff-only
chezmoi diff
chezmoi apply
dotfiles-doctor
```

## Safari MCP

safari 그룹을 켜면 Claude Code와 Codex에 `safaridriver --mcp`를 등록한다. Safari 설정 → 고급에서 웹 개발자 기능을 켜고, 개발자 설정에서 원격 자동화와 외부 에이전트를 허용한다. ([WebKit 안내](https://webkit.org/blog/18136/introducing-the-safari-mcp-server-for-web-developers/))

## Chrome Canary MCP

canary 그룹의 chrome-devtools MCP는 실행 중인 Canary(로그인된 프로필)에 붙는다.

1. Canary를 켜고 `chrome://inspect/#remote-debugging`에서 원격 디버깅을 켠다.
2. Claude Code나 Codex를 다시 시작한다.
3. Canary에 뜨는 연결 허용 창에서 허용한다.
