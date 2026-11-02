# Orange 的版本更新

电视上发现新版 → 点一下 → 下载 → 装好，全在这一套里。代码大部分沿用上游 Aerial，
Orange 只是把它的身份和地址换成了自己的。

---

## 链路

```
Orange 桌面（每 3 小时，或打开控制中心时）
        │  GET version.json
        ▼
gh-proxy（GitHub 中转）──失败──▶ raw.githubusercontent ──失败──▶ jsDelivr
        │ 一个文本通了就算成功
        ▼
26.4 > 26.3.6 ？
        │ 是
        ▼
控制中心「发现新版本 26.4」 + 首页每天一次的 8 秒轻提示
        │ 点「下载并安装」
        ▼
下载 3 MB APK（json 里的 apk 地址 → 内置地址依次兜底）
        │
        ▼
校验：包名一致、签名一致、versionCode 不比现在低
        │
        ▼
PackageInstaller 会话  ──▶  系统弹窗「要安装此应用吗」
        │                        │
        │                  遥控器按「安装」
        ▼                        ▼
    覆盖安装，数据和设置全部保留，Orange 重启
```

### 为什么 JSON 里写 APK 的完整地址

换托管位置只要改 `version.json` 一个字段，不用发新版。`UpdateSource.APK_URLS` 只是
JSON 没写 `apk` 时的兜底。

### 为什么第一个源是 GitHub 中转

这一条是实测出来的，不是猜的。在这台电视（小米 MiTV-ASTP0，家庭宽带）上用 `nc` 一个
一个试 443 端口：

| 站点 | 结果 |
|---|---|
| `github.com` | 连不上 |
| `raw.githubusercontent.com` | 连不上 |
| `cdn.jsdelivr.net` | 连不上 |
| `gh-proxy.com`（GitHub 中转） | 通 |
| `gitee.com` | 通 |

所以电视端的第一个源是 `gh-proxy.com` 拼在 GitHub raw 前面，直连地址只作为第二、第三
兜底（在 GitHub 本身能通的网络里就会命中它们）。文件都放在仓库的 `update/` 目录，发版
就是一次 `git push`，不需要 Release、不需要令牌。

> 注意：如果在开着代理的电脑上测，结论会完全相反——代理让 github.com 变通，于是看起来
> 一切正常，电视上却查不到更新。测这类问题必须在电视上测。

### 中转挂了怎么办

`gh-proxy.com` 是志愿者的服务，不保证永久。它挂了不影响已装的应用运行，只是查不到新
版本。补救办法有两个，都不用发新版：

- 换 `UpdateSource.MIRROR` 常量（需要发一次版）
- 或者把 `version.json` 的 `apk` 字段指到别处（改文件即可，下次检查就生效）

真要长期稳定，把这两个文件放到国内对象存储（阿里云 OSS / 腾讯云 COS 默认域名，免备案，
几块钱一年），然后改 `version.json` 的 `apk` 字段指向它即可。

### 安全

`Updates.sameSigner()` 会比对下载的 APK 与本机签名，不一致直接拒绝。所以即使 JSON
被劫持指向别的 APK，也装不进去；最坏结果只是更新失败。

---

## 首次接入

1. **备份 `local/aerial-release.jks`**（和 `signing.properties`）到两个地方。
   这个文件丢了，现在这批用户就再也收不到自动更新，只能卸载重装。

2. 建一个 GitHub 仓库，把工程推上去。

3. 改 `app/src/main/java/app/orchard/tv/update/UpdateSource.kt` 里的 `REPO`：

   ```kotlin
   const val REPO = "你的用户名/仓库名"
   ```

   只改这一处，三个源都会跟着变。

4. 仓库里要有一个 `update/` 目录（发版脚本会自动建）：

   ```
   update/
   ├── version.json     版本说明，几十行
   └── orange.apk       永远是当前最新版，固定文件名
   ```

---

## 发版

```bash
./tools/release.sh 26.4 "打赏页支持长按返回键关闭" "一键优化更懂后台进程"
```

脚本会：

1. 按 `26.Y.P → 26×100 + Y×10 + P` 算 versionCode，改 `app/build.gradle.kts`
2. `assembleStandardRelease`（本机 8GB 内存，脚本里已经把堆限制在 2g，R8 约 13 分钟）
3. 把 APK 复制成 `update/orange.apk`（电视从这里取）和 `dist/orange-26.4.apk`（留档）
4. 写 `update/version.json`
5. `git commit && git push`（走 SSH，不需要令牌）
6. 有 `gh` 就顺手发 GitHub Release，没有就跳过——**发布不需要 Release，push 就够了**
7. 通过中转读一次线上的 `version.json`，确认电视能看到新版本号

### version.json

```json
{
  "version": "26.4",
  "apk": "https://gh-proxy.com/https://raw.githubusercontent.com/你的用户名/仓库名/main/update/orange.apk",
  "notes": [
    "打赏页支持长按返回键关闭",
    "一键优化更懂后台进程"
  ]
}
```

- `version` 必填，`26.4` 或 `26.4.1`。写成 `v26.4` 也认；纯文本文件（只有一行版本号）也认，
  方便手工调试。
- `apk` 可选，必须是 https。不写就用内置地址。
- `notes` 可选，最多显示 4 条（多了会截断，电视上放不下）。
- 解析实现在 `UpdateSource.parse()`，边界情况见 `app/src/test/.../UpdateSourceTest.kt`。

### 发布前

- 改 `versionCode` 单调递增，否则电视判定「不比现在新」而拒绝。
- 电视的检查节奏：开机 / 回到桌面时查一次，**3 小时内不重复**；打开控制中心时门限是
  **10 分钟**。所以发完版想马上在电视上看到，进一次控制中心即可，不用等 3 小时。

---

## 电视上的表现

| 位置 | 什么时候出现 |
|---|---|
| 首页顶部一行 | 发现新版后，回桌面 5 秒浮出，8 秒消失，**每天最多一次** |
| 控制中心 | 常驻入口「发现新版本 26.4 ›」，点进去是完整更新页（版本号、更新说明、下载按钮、失败原因、手机扫码兜底） |

首页那一行**不抢遥控器焦点**（`detectTapGestures`，不是 `clickable`），按方向键不会被它劫走。

---

## 已知限制

- **Android 9 及以下必须用户按一次「安装」**。图上那一步是系统的 `PackageInstaller`
  确认页，绕不过去；只有 Android 12+ 才有 `USER_ACTION_NOT_REQUIRED` 可以真正静默。
  系统级静默安装要 `INSTALL_PACKAGES`（系统签名或 root），普通应用拿不到。
- **26.3.x 及更早的版本收不到提示**：那些版本的 `Updates.official` 是 false（当时
  `PACKAGE` 还是 `app.orchard.tv`）。需要一个手动安装的版本跨过去，之后才进入自动更新。
- **仓库会慢慢变大**：`update/orange.apk` 每次覆盖，但 git 历史保留旧 blob，
  大约每个版本 3 MB。二三十个版本后可以 squash 一次历史。
- **降级不可用**：versionCode 检查会拒绝旧版。要回退只能卸载重装（会丢设置）。
