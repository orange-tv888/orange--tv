#!/usr/bin/env bash
# 发布一个新版本：体检 → 改版本号 → 打包 → 写版本说明 → 提交 → 推送 → 发 Release。
#
#   ./tools/release.sh 26.4.2 "说明一" "说明二"
#   ./tools/release.sh --check 26.4.2      # 只体检：不编译、不提交、不推送、不改任何文件
#
# 第一个参数是版本号，后面每一条是一条更新说明（最多四条，电视上一条一行）。
# 版本号规则：26.Y 或 26.Y.P；versionCode = 年份×100 + Y×10 + P，所以 26.4.2 = 2642。

set -euo pipefail

# 沙箱里 ~/.ssh/known_hosts 读不了，git push 会卡在「是否信任 github.com」的提问上。
export GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/tmp/known_hosts_orange"
export GIT_TERMINAL_PROMPT=0

HERE=$(cd "$(dirname "$0")/.." && pwd)
cd "$HERE"

die()  { printf "\033[31m✗ %s\033[0m\n" "$*" >&2; exit 1; }
warn() { printf "\033[33m! %s\033[0m\n" "$*" >&2; }
note() { printf "\033[90m· %s\033[0m\n" "$*"; }

# ---- 0. 参数 ----
CHECK=0
if [ "${1:-}" = "--check" ]; then CHECK=1; shift; fi
[ $# -ge 1 ] || die "用法：./tools/release.sh [--check] <版本号> [更新说明…]"

VER=$1; shift
NOTES=("$@")

echo "$VER" | grep -Eq '^26\.[0-9]+(\.[0-9]+)?$' || die "版本号要写成 26.4 或 26.4.2 这样（收到：${VER}）"
[ ${#NOTES[@]} -le 4 ] || die "更新说明最多 4 条，电视上放不下更多"

IFS=. read -r YEAR MINOR PATCH <<< "$VER"
PATCH=${PATCH:-0}
CODE=$(( YEAR * 100 + MINOR * 10 + PATCH ))

# ---- 1. 永远不进发布提交的文件 ----
# 用拒绝清单而不是白名单：白名单会在新增目录时漏提交，那比多提交一张截图更糟。
denied() {
    case "$1" in
        dist/shots/*|screenshots/*|*.log|*.mp4|*.mov|*.png.orig|.DS_Store|*/.DS_Store) return 0 ;;
    esac
    return 1
}

# 「这次会提交什么」——--check 与正式发版共用，不允许体检和实跑是两套逻辑。
commit_plan() {
    local p pending="" blocked=""
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        p=$(printf '%s' "$line" | /usr/bin/sed -e "s/^[a-z]* '//" -e "s/'$//")
        if denied "$p"; then blocked="$blocked    $p
"; else pending="$pending    $p
"; fi
    done < <(git add -A --dry-run 2>/dev/null || true)
    if [ -n "$pending" ]; then
        note "会提交："
        printf '%s' "$pending"
    else
        note "会提交：（没有改动）"
    fi
    if [ -n "$blocked" ]; then
        note "被挡下（调试截图 / 录屏 / 日志，不进仓库）："
        printf '%s' "$blocked"
    fi
}

# ---- 2. 版本号一致性 ----
gradle_ver()  { grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2; }
gradle_code() { grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | awk '{print $3}'; }

check_pin() {
    local gv gc
    gv=$(gradle_ver); gc=$(gradle_code)
    [ "$gv" = "$VER" ] || die "build.gradle.kts 里是 ${gv}，跟要发的 ${VER} 对不上（版本号要先改好再发）"
    [ "$gc" = "$CODE" ] || die "build.gradle.kts 里 versionCode 是 ${gc}，按公式 ${VER} 应该是 ${CODE}"
}

# 单一真相：仓库地址只写在 UpdateSource.kt 里。
REPO=$(grep -oE 'REPO = "[^"]+"' app/src/main/java/app/orange/tv/update/UpdateSource.kt | cut -d'"' -f2)
[ -n "$REPO" ] || die "在 UpdateSource.kt 里找不到 REPO"
PUSH_URL="ssh://git@ssh.github.com:443/$REPO.git"
git rev-parse --git-dir >/dev/null 2>&1 || die "这里还不是 git 仓库。先 git init 并接上 GitHub 远端。"

# ---- 3. 体检模式：只看不发 ----
if [ "$CHECK" = 1 ]; then
    note "体检 ${VER}（versionCode ${CODE}），只读，不改任何文件"
    check_pin
    note "版本号与 build.gradle.kts 一致"

    if [ -f update/version.json ]; then
        note "线上当前：$(grep -oE '"version": "[^"]+"' update/version.json | cut -d'"' -f4)"
    fi

    commit_plan

    AHEAD=$(git rev-list --count "$(git rev-parse --abbrev-ref HEAD)" --not --remotes 2>/dev/null || echo 0)
    [ "${AHEAD:-0}" = "0" ] || warn "本地有 ${AHEAD} 个提交没推上去"

    if GIT_SSH_COMMAND="ssh -p 443 -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/tmp/known_hosts_orange -o ConnectTimeout=8" \
        git ls-remote --heads "$PUSH_URL" main >/dev/null 2>&1; then
        note "推送通道 OK（ssh.github.com:443）"
    else
        warn "推送通道不通（22 和 443 都连不上），这次发版会卡在推送"
    fi

    note "体检通过，可以发：./tools/release.sh ${VER} \"说明…\""
    exit 0
fi

# ---- 4. 改版本号 ----
note "当前 $(gradle_ver) → 将发布 ${VER}（versionCode ${CODE}）"
/usr/bin/perl -pi -e "s/versionCode = [0-9]+/versionCode = $CODE/" app/build.gradle.kts
/usr/bin/perl -pi -e "s/versionName = \"[^\"]+\"/versionName = \"$VER\"/" app/build.gradle.kts
check_pin

# ---- 5. 打包 ----
note "编译中（R8 是最慢的一步）…"
JAVA_HOME=${JAVA_HOME:-/Users/jiamingcai/WorkBuddy/2026-10-29-13-04-49/toolchain/jdk-17.0.20.1+1/Contents/Home}
export JAVA_HOME
# 这台 Mac 只有 8GB 内存：默认的 -Xmx4g 会让 R8 被系统 OOM 杀掉（exit 137）。
./gradlew assembleStandardRelease --console=plain --max-workers=2 \
    -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8" >/dev/null \
    || die "编译失败，看 ./gradlew assembleStandardRelease 的输出"

APK=$(ls -t app/build/outputs/apk/standard/release/*.apk | head -1)
[ -n "$APK" ] || die "没找到打出来的 APK"
note "产物 $(basename "$APK") ($(du -h "$APK" | cut -f1))"

# ---- 6. 分发 + 校验三份同源 ----
mkdir -p update dist
cp "$APK" "update/orange.apk"                     # jsDelivr 从这里取，永远是这一份
cp "$APK" "dist/orange-$VER.apk"                  # 本地留档，也方便直接发给别人

H1=$(shasum "$APK" | awk '{print $1}')
H2=$(shasum update/orange.apk | awk '{print $1}')
H3=$(shasum "dist/orange-$VER.apk" | awk '{print $1}')
[ "$H1" = "$H2" ] && [ "$H1" = "$H3" ] \
    || die "三份 APK 不一致（构建 ${H1:0:8} / update ${H2:0:8} / dist ${H3:0:8}），别发"
note "三份同源 ${H1:0:8}"

# ---- 7. 新电视安装包跟着换 ----
# 这一步以前靠人记，漏过两次（装到新电视上是旧版）。放进脚本里，忘不掉。
if [ -f dist/newtv/payload/orange.apk ]; then
    cp update/orange.apk dist/newtv/payload/orange.apk
    if [ -f dist/orange-newtv-setup.zip ]; then
        ( cd dist && zip -q -X orange-newtv-setup.zip newtv/payload/orange.apk )
        unzip -l dist/orange-newtv-setup.zip | grep -q "newtv/payload/orange.apk" \
            || die "安装包重打失败：zip 里找不到 payload"
        note "新电视安装包已换上新包（$(ls -lh dist/orange-newtv-setup.zip | awk '{print $5}')）"
    fi
fi

# ---- 8. 版本说明 ----
{
    printf '{\n  "version": "%s",\n' "$VER"
    printf '  "apk": "https://ghproxy.net/https://raw.githubusercontent.com/%s/main/update/orange.apk",\n' "$REPO"
    if [ ${#NOTES[@]} -gt 0 ]; then
        printf '  "notes": [\n'
        for i in "${!NOTES[@]}"; do
            line=$(printf '%s' "${NOTES[$i]}" | /usr/bin/sed 's/\\/\\\\/g; s/"/\\"/g')
            if [ "$i" -lt $(( ${#NOTES[@]} - 1 )) ]; then
                printf '    "%s",\n' "$line"
            else
                printf '    "%s"\n' "$line"
            fi
        done
        printf '  ]\n'
    else
        printf '  "notes": []\n'
    fi
    printf '}\n'
} > update/version.json

note "版本说明："
/usr/bin/sed 's/^/    /' update/version.json

# ---- 9. 提交（只提交该提交的） ----
git add -A
while IFS= read -r p; do
    [ -n "$p" ] || continue
    if denied "$p"; then git reset -q -- "$p"; warn "挡下 $p"; fi
done < <(git diff --cached --name-only)

STAGED=$(git diff --cached --name-only | wc -l | tr -d ' ')
if [ "$STAGED" = "0" ]; then
    note "没有改动可提交"
else
    note "提交 ${STAGED} 个文件"
    git commit -q -m "Orange $VER"
fi

# ---- 10. 推送（22 不通就退 443） ----
if ! git push -q; then
    note "22 端口不通，改走 ssh.github.com:443"
    GIT_SSH_COMMAND="ssh -p 443 -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/tmp/known_hosts_orange" \
        git push -q "$PUSH_URL" HEAD:main \
        || die "git push 失败（22 和 443 都不通，检查网络与密钥）"
fi
# 走显式 URL 推送不会更新 origin/main，本地会一直显示 ahead，容易误判没推上去。
git update-ref refs/remotes/origin/main HEAD 2>/dev/null || true
COMMIT=$(git rev-parse --short HEAD)
note "已推送 $COMMIT"

# ---- 11. 发 Release ----
if command -v gh >/dev/null 2>&1; then
    gh release create "v$VER" "dist/orange-$VER.apk#orange.apk" \
        --title "Orange $VER" \
        --notes "$(printf '%s\n' "${NOTES[@]:-}")" >/dev/null \
        && note "Release v$VER 已发布" \
        || note "gh 发 Release 失败，去网页手动传 dist/orange-$VER.apk"
else
    note "没装 gh。去 https://github.com/$REPO/releases/new 手动传 dist/orange-$VER.apk，tag 写 v$VER"
fi

# ---- 12. 确认线上能读到 ----
# 电视第一个问的是 GitHub 中转，所以这里也用中转去读，读到什么就是电视会看到什么。
MIRROR="https://ghproxy.net/https://raw.githubusercontent.com/$REPO/main/update/version.json"
if curl -sf --max-time 25 --noproxy '*' "$MIRROR" | grep -q "\"$VER\""; then
    note "中转已能读到 $VER"
else
    note "中转暂时还没读到 ${VER}（可能缓存几分钟），电视那次检查会自己重试"
fi

echo
printf "\033[32m发布完成：Orange %s（versionCode %s）\033[0m\n" "$VER" "$CODE"
echo "  电视上最迟一次开机后就会看到提示。"
echo "  想立刻验证：控制中心 → Orange $VER → 检查更新。"
