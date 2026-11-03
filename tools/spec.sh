#!/usr/bin/env bash
# 开一个需求：从模板生成需求单 / 测试单 / 复盘单，并把今页看板里的"进行中"占位。
#
#   tools/spec.sh new L 26.5.0-boot-intro "开机字标动画"
#   tools/spec.sh list
#
# 档位：L 大改（全套）/ M 功能（需求+测试+三行复盘）/ S 小修（只登记在看板，不建单）

set -euo pipefail

HERE=$(cd "$(dirname "$0")/.." && pwd)
cd "$HERE"

die() { printf '\033[31m✗ %s\033[0m\n' "$*" >&2; exit 1; }
note() { printf '\033[90m· %s\033[0m\n' "$*"; }

CMD=${1:-help}

case "$CMD" in
new)
    TIER=${2:-} ; ID=${3:-} ; TITLE=${4:-}
    [ -n "$TIER" ] && [ -n "$ID" ] && [ -n "$TITLE" ] \
        || die "用法：tools/spec.sh new <L|M|S> <id> \"标题\""
    case "$TIER" in L|M|S) ;; *) die "档位只能是 L / M / S（收到：$TIER）" ;; esac
    # id 约定：版本号-短名，用来跟分支、提交、发版说明对上。
    echo "$ID" | grep -Eq '^[0-9]+\.[0-9]+(\.[0-9]+)?-[a-z0-9-]+$' \
        || die "id 要写成 26.5.0-boot-intro 这样（收到：$ID）"

    mkdir -p docs/spec docs/qa docs/retro

    if [ "$TIER" = "S" ]; then
        note "S 档不建单，只登记：在 docs/NOW.md 的「进行中」加一行即可"
        note "S 档唯一的硬要求：G3 要有一条证据（截图或日志行）"
        exit 0
    fi

    [ -e "docs/spec/$ID.md" ] && die "docs/spec/$ID.md 已存在，换个 id 或直接改它"

    # sed 在沙箱里会被换成 toybox 版（不认 \s），标题里还可能有斜杠和 &，一律用 perl。
    TITLE="$TITLE" ID="$ID" TIER="$TIER" /usr/bin/perl -pe '
        s/<标题>/$ENV{TITLE}/;
        s/^- id：.*/"- id：`$ENV{ID}`"/e;
        s/^- 档位：.*/"- 档位：$ENV{TIER}"/e;
    ' docs/spec/TEMPLATE.md > "docs/spec/$ID.md"

    TITLE="$TITLE" ID="$ID" /usr/bin/perl -pe '
        s/<标题>/$ENV{TITLE}/;
        s/^- id：.*/"- id：`$ENV{ID}`"/e;
    ' docs/qa/TEMPLATE.md > "docs/qa/$ID.md"

    note "建好了："
    note "  docs/spec/$ID.md   ← PM 先填前四段"
    note "  docs/qa/$ID.md     ← QA 照第四段抄判据"

    if [ "$TIER" = "L" ]; then
        note "L 档还需要："
        note "  分支  git checkout -b feat/$ID"
        note "  复盘  docs/retro/$(echo "$ID" | cut -d- -f1).md（发版后 24h 内）"
    else
        note "M 档发版后在 docs/retro/ 写三行即可"
    fi
    ;;

list)
    printf '\033[1m需求单\033[0m\n'
    ls docs/spec/*.md 2>/dev/null | grep -v TEMPLATE || echo "  （空）"
    printf '\033[1m测试单\033[0m\n'
    ls docs/qa/*.md 2>/dev/null | grep -v TEMPLATE || echo "  （空）"
    printf '\033[1m复盘\033[0m\n'
    ls docs/retro/*.md 2>/dev/null | grep -v TEMPLATE || echo "  （空）"
    ;;

*)
    cat <<'EOF'
Orange 需求脚手架

  tools/spec.sh new <L|M|S> <id> "标题"   开一个需求（S 档只登记，不建单）
  tools/spec.sh list                     看看手上有哪些单子

档位决定要几张单子，流程见 docs/process/WORKFLOW.md
EOF
    ;;
esac
