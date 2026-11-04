# 环境与坑速查

> 新 session 开工先扫一遍这张表，能省掉半小时摸索。带 ★ 的是踩过两次以上的。

## 构建

| 要什么 | 命令 | 耗时 |
|---|---|---|
| 只查类型错 | `./gradlew :app:compileStandardReleaseKotlin` | 1.5–4 分钟 |
| 装机包（快验，跳过 lint） | `./gradlew :app:assembleStandardRelease -x lintVitalStandardRelease` | 约 3–4 分钟 |
| 发版包（必跑 lint） | `./tools/release.sh <版本> "说明"` | 8–16 分钟 |

- ★ `JAVA_HOME=/Users/jiamingcai/WorkBuddy/2026-10-29-13-04-49/toolchain/jdk-17.0.20.1+1/Contents/Home`（本机没有系统 Java）。
- ★ gradle 必须在**提权（关闭沙箱）**下跑，否则会卡在 kotlin-daemon 的 `file-delete` 上，十几分钟后失败。
- ★ 默认 `-Xmx4g` 会被 8GB 内存的机器 OOM 杀掉（exit 137），固定加 `--max-workers=2 -Dorg.gradle.jvmargs="-Xmx2g -XX:MaxMetaspaceSize=512m"`。
- 慢的大头是 `lintVitalAnalyzeStandardRelease`（约 9 分钟），不是 R8。build 目录 5 分钟没产出是正常的。

## 电视

- adb：`/Users/jiamingcai/WorkBuddy/2026-10-31-09-27-17/aerial-tv/tools/platform-tools/adb`，设备 `192.168.1.11:5555`（IP 会变，先 `adb connect`）。
- 装机必须用 release 包（debug 签名不同，`INSTALL_FAILED_UPDATE_INCOMPATIBLE`；debug 在电视上还会卡）。
- ★ **别用 `am force-stop` + `am start -S` 测冷启动**：Orange 是 Launcher，force-stop 后系统会自动拉起并播一次动画，再 start 就是第二次启动，中间黑屏会被误判成 bug。
- ★ **别用 screenrecord 判断渲染**：只有 4.5–15fps，H.264 Baseline 在画面剧变时有宏块伪影。判断渲染一律 `screencap` 无损帧。
- `am force-stop` 杀不掉 guard 守护，杀进程后 App 会自动回来。

## 推送与发版

- ★ 本机 SSH 22 端口到 GitHub 常年不通，push 走 443：`release.sh` 已内置兜底。
- ★ 后台任务里读不到 `~/.ssh/id_ed25519`（沙箱拒读），push 必须**前台 + 提权**。
- 版本号 `26.Y.P`，versionCode = 年份×100 + Y×10 + P（26.4.2 → 2642）。真值只有 `tools/release.sh` 里那一处公式。
- 发版后三份 APK 对哈希：`dist/orange-<ver>.apk`、`update/orange.apk`、构建产物，SHA1 一致才算发出去。
- 电视读的是 `update/version.json`，走 ghproxy.net 中转，不是 GitHub Release。中转推完立刻可读。
- 本机没装 `gh`，`Release` 页要网页手动建（不影响更新通道）。
- 电视侧检查更新的门限：控制中心 10 分钟、首页 3 小时（`PANEL_MAX_AGE_MS` / `MAX_AGE_MS`）。
- `git add -A` 会把工作区里的截图等无关文件一起提交，发版前先 `git status` 看一眼。

## 判断"好没好"的手法

- 视觉：**contact sheet**（多帧拼一张），不要一张一张看。
- 动画揭幕：逐帧算平均亮度，看序列（例：字标帧 → 全黑 1 帧 → 9/18/110 三帧亮起）。
- 抖动：静止连拍 6–10 张，逐像素差应为 0；有差异就做互相关定位位移。
- 卡顿：`dumpsys gfxinfo <pkg> framestats` 看帧数与超时帧，`PerfMonitor longMsg` 看主线程长任务。
- 误判提醒：**在 Mac 上测电视的网络会得出完全错误的结论**（Mac 有代理）。网络问题一律在电视上用 `toybox nc -w 6 <host> 443` 测。

## 命令形态的坑（会让人误判"已经成功"）

- **`git push ... | tail -3` 会吃掉退出码**：管道最后一段是 `tail`，`&&` 只看得到它成功，于是 push 失败也被当成功，
  接着 `git update-ref origin/main HEAD` 还会把本地远端指针改成 HEAD，`git status` 显示"已同步"——全靠假象。
  实测代价：一次提交显示"已推送"，远端 API 查到的还是上一个 commit。**推送要单独跑、看 `$?`，不要接管道。**
- 同理 `cmd | tail`／`cmd | head` 都会掩盖中途失败；要看结果就 `cmd > /tmp/out 2>&1; echo $?; tail /tmp/out`。
- 双引号里再嵌 `"` 写提交信息会被拆词，`-m` 参数悄悄变形（这次 commit 信息丢了两侧的引号）。
  中文提交信息里别用英文双引号，改用直角引号或去掉。
