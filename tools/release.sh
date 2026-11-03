#!/usr/bin/env bash
# 发布一个新版本：改版本号 → 打包 → 写版本说明 → 提交 → 发 Release。
#
#   ./tools/release.sh 26.4 "打赏页支持长按返回键关闭" "一键优化更懂后台进程"
#
# 第一个参数是版本号，后面每一条是一条更新说明（最多四条，电视上一条一行）。
# 版本号规则：26.Y 或 26.Y.P；versionCode = 26×100 + Y×10 + P，所以 26.4 = 2640。

set -euo pipefail

# 沙箱里 ~/.ssh/known_hosts 读不了，git push 会卡在「是否信任 github.com」的提问上。
export GIT_SSH_COMMAND="ssh -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/tmp/known_hosts_orange"
export GIT_TERMINAL_PROMPT=0

HERE=$(cd "$(dirname "$0")/.." && pwd)
cd "$HERE"

die() { printf "\033[31m✗ %s\033[0m\n" "$*" >&2; exit 1; }
note() { printf "\033[90m· %s\033[0m\n" "$*"; }

[ $# -ge 1 ] || die "用法：./tools/release.sh <版本号> [更新说明…]"

VER=$1; shift
NOTES=("$@")

echo "$VER" | grep -Eq '^26\.[0-9]+(\.[0-9]+)?$' || die "版本号要写成 26.4 或 26.4.1 这样（收到：${VER}）"
[ ${#NOTES[@]} -le 4 ] || die "更新说明最多 4 条，电视上放不下更多"

IFS=. read -r YEAR MINOR PATCH <<< "$VER"
PATCH=${PATCH:-0}
CODE=$(( YEAR * 100 + MINOR * 10 + PATCH ))

# 单一真相：仓库地址只写在 UpdateSource.kt 里。
REPO=$(grep -oE 'REPO = "[^"]+"' app/src/main/java/app/orchard/tv/update/UpdateSource.kt | cut -d'"' -f2)
[ -n "$REPO" ] || die "在 UpdateSource.kt 里找不到 REPO"
note "仓库 $REPO"

git rev-parse --git-dir >/dev/null 2>&1 || die "这里还不是 git 仓库。先 git init 并接上 GitHub 远端。"

CUR=$(grep -oE 'versionName = "[^"]+"' app/build.gradle.kts | cut -d'"' -f2)
note "当前 $CUR → 将发布 ${VER}（versionCode ${CODE}）"

# ---- 1. 版本号 ----
/usr/bin/perl -pi -e "s/versionCode = [0-9]+/versionCode = $CODE/" app/build.gradle.kts
/usr/bin/perl -pi -e "s/versionName = \"[^\"]+\"/versionName = \"$VER\"/" app/build.gradle.kts

# ---- 2. 打包 ----
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

# ---- 3. 分发 ----
mkdir -p update dist
cp "$APK" "update/orange.apk"                     # jsDelivr 从这里取，永远是这一份
cp "$APK" "dist/orange-$VER.apk"                  # 本地留档，也方便直接发给别人

# ---- 4. 版本说明 ----
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
cat update/version.json | /usr/bin/sed 's/^/    /'

# ---- 5. 提交 ----
git add -A
git commit -q -m "Orange $VER" || note "没有改动可提交"
# 22 端口在国内经常被掐断（Connection closed / 超时）。GitHub 官方留了 ssh.github.com:443 备用通道，
# 直连失败就退到那里；两条都不通才是真的网络或密钥问题。
if ! git push -q; then
    note "22 端口不通，改走 ssh.github.com:443"
    GIT_SSH_COMMAND="ssh -p 443 -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/tmp/known_hosts_orange" \
        git push -q "ssh://git@ssh.github.com:443/$REPO.git" HEAD:main \
        || die "git push 失败（22 和 443 都不通，检查网络与密钥）"
fi
COMMIT=$(git rev-parse --short HEAD)
note "已推送 $COMMIT"

# ---- 6. 发 Release ----
if command -v gh >/dev/null 2>&1; then
    gh release create "v$VER" "dist/orange-$VER.apk#orange.apk" \
        --title "Orange $VER" \
        --notes "$(printf '%s\n' "${NOTES[@]:-}")" >/dev/null \
        && note "Release v$VER 已发布" \
        || note "gh 发 Release 失败，去网页手动传 dist/orange-$VER.apk"
else
    note "没装 gh。去 https://github.com/$REPO/releases/new 手动传 dist/orange-$VER.apk，tag 写 v$VER"
fi

# ---- 7. 确认线上能读到 ----
# 电视第一个问的是 GitHub 中转，所以这里也用中转去读，读到什么就是电视会看到什么。
MIRROR="https://ghproxy.net/https://raw.githubusercontent.com/$REPO/main/update/version.json"
if curl -sf --max-time 25 --noproxy '*' "$MIRROR" | grep -q "\"$VER\""; then
    note "中转已能读到 $VER"
else
    note "中转暂时还没读到 ${VER}（可能缓存几分钟），电视那次检查会自己重试"
fi

echo
printf "\033[32m\033[0m发布完成：Orange %s（versionCode %s）\n" "$VER" "$CODE"
echo "  电视上最迟一次开机后就会看到提示。"
echo "  想立刻验证：控制中心 → Orange $VER → 检查更新。"
