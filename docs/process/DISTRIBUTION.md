# 对外分发（要不要让别人下载 Orange）

> **探讨记录，不是执行手册，也不是决策。**
> 2026-11-03 晚和用户聊到"能不能放 GitHub 上给别人下载"，用户 23:30 明确：**「先别放上去公开发行，刚刚只是探讨，不是执行」**。
> 所以本文只是把查证到的事实和几条路的取舍记下来。**仓库、脚本、gitignore 一律没动**，
> 唯一改动是这份文档本身（且尚未提交）。等哪天真的要做，从第六节的清单开始。

## 一、查证到的现状（事实，非决定）

- **仓库是公开的**：`private: false`，2026-11-02 创建。
- **Releases 是空的 `[]`**：没有任何下载页。
- **`dist/` 里有 4 份带版本号的 APK 被 git 跟踪**（26.3.7 / 26.4 / 26.4.1 / 26.4.2），
  所以严格说"别人现在就能从仓库下到安装包"，只是没有入口指路。
- **更新通道依赖公开读**：电视读 `update/version.json` 和 `update/orange.apk`，走 ghproxy 中转。
  这两份必须匿名可读 —— 这是"仓库不能转私有"的唯一硬约束。
- ~~**门面仍是上游 Aerial**：README 标题 `# Aerial`，下载徽章指向 `pengchujin/aerial-tv`。~~
  **已于 2026-11-04 改掉**，见第二节。
- ~~**LICENSE 是 MIT、版权人 pengchujin**。~~
  **已补第二行** `Copyright (c) 2026 JC Aerial — modifications in Orange`，原文保留。

> **2026-11-04 补记**：下面第二、三节是 11-03 的判断，门面那部分已被 26.5.0 的改名做完了。
> 整体结论没变——**不对外发布，但更新照走**（口径见 `docs/process/` 与 `docs/release/CHECKLIST.md`）。

### 一个必须记住的技术事实

只要"在线更新"要继续，**`update/orange.apk` 就必然可被匿名下载**。这是机制决定的，不是配置问题。
所以"完全不公开"和"保留在线更新"不能同时满足，只能取舍。

---

### 证据（2026-11-03 23:15 实测）

| 事实 | 证据 |
|---|---|
| 仓库 public | `api.github.com/repos/orange-tv888/orange--tv` → `"private": false` |
| 创建时间 | `2026-11-02T01:49:20Z` |
| 无需鉴权可读 | `raw.githubusercontent.com/.../main/update/version.json` → HTTP 200 |
| **APK 在 git 里** | `dist/orange-26.4.2.apk` 3,333,068 字节，26.4.1 / 26.4 / 26.3.7 同样在 |
| Releases | `[]` —— 空 |
| 仓库体积 | 23MB（`size: 23016` KB） |

## 二、~~现在的门面还不叫 Orange~~ 门面已于 2026-11-04 换成 Orange

> 本节描述的是 11-03 的状态，**已经不再成立**，留在这里是为了记住当时最要命的那条：
> 「别人点下载，下到的是上游原版」。这个问题现在没了——`tools/install.sh` 改成从本仓库
> `update/orange.apk` 取包，README / site 的上游链接全部指向本仓库。

App 内部品牌早已换完（`strings.xml` 里 `app_name=Orange`、`guard_name=Orange Home Guard`），
11-03 当时对外的三层还是上游品牌：

| 位置 | 11-03 的现状 | 11-04 之后 |
|---|---|---|
| `README.md` | 标题 `# Aerial`、下载徽章指向 `pengchujin/aerial-tv/releases/latest` | 标题 `# Orange`，下载表指向本仓库 `update/orange.apk`，末尾留「来源」段署上游 |
| `README.en.md` | 同上 | 同上 |
| `site/index.html` | 36 处 `Aerial` / `pengchujin` / `aerial.qust.me` | 品牌词清零，`app.js` 的下载与安装脚本回退到本仓库 raw |
| `LICENSE` | 只有 `Copyright (c) 2026 pengchujin` | 追加 `Copyright (c) 2026 JC Aerial — modifications in Orange`，原文不动 |
| 应用图标 | 上游绿紫渐变 | 橙色渐变，`mipmap-*` 五个密度与 `docs/brand/icon.svg` 几何一致 |
| 仓库 About | `description` / `homepage` 空 | **仍是空**，属于 GitHub 网页上的手动动作，不阻塞发版 |

曾有的后果（**已消除**）：别人点 README 里的「下载 APK」，下到的是上游原版，不是 Orange。

## 三、三条路与代价

| 方案 | 做什么 | 代价 / 风险 |
|---|---|---|
| **A. 补 Release + 门面换 Orange**（推荐） | 建 26.4.2 的 Release 带 APK 与更新说明；README 重写为 Orange；图标换成自己的；下载按钮指向自己的 Release，另加 ghproxy 国内镜像；LICENSE 保留上游 MIT 并追加自己的版权行 | 要重截 `site/img/` 截图（现有截图是上游 Aerial 界面）；约半天 |
| **B. 只补 Release** | 建 Release、传 APK。README 不动 | 最快（10 分钟），但访客仍会被 README 引到上游版本；等于半个误导 |
| **C. 源码转私有 + 另开只放 APK 的公开仓库** | `settings` 里转 private；新仓库只放 APK + `version.json` | **代价最硬**：电视的在线更新读的是公开 raw 地址，转私有后立刻 404，在线更新断掉。必须先把更新地址迁到非 GitHub 托管（`wrangler.jsonc` 已有 Cloudflare 配置，`aerial.qust.me` / `tv.qust.me`），否则你电视上装的 26.4.2 再也收不到更新提示。工作量大，且中间有一段更新空窗 |

**注意方案 C 的隐藏成本**：即使转了私有，`dist/*.apk` 仍留在 git 历史里，仓库一旦再次公开就全部可见。想真删干净得 `git filter-repo` 重写历史（会改变所有 commit hash，已发的 Release 引用会失效）。现在 23MB 不算大，不建议为这个动历史。

## 四、MIT 是硬约束，不是提醒

`LICENSE` 是 MIT、版权人是 pengchujin。MIT 明确允许修改、改名、再分发、商用，**但有两个条件不可绕过**：

1. **必须保留原版权声明和许可声明**。所以 `LICENSE` 里的 `Copyright (c) 2026 pengchujin` 不能删、不能改成自己的名字一了百了。正确做法是保留原文，另起一行加 `Copyright (c) 2026 <你的名字/组织>`。
2. **分发时须随附许可副本**。严格说，APK 装到用户机器上也算分发，所以 App 内应该能找到许可文本（设置 → 关于 → 开源许可）。这一项目前**没有**，属于待补的合规缺口。

另外 README 里写一句来源（"基于 Aerial (MIT) 修改"）既是合规，也是体面——上游是别人几年的工作量。

## 五、风险披露文案（备好，等公开时直接贴）

用户 2026-11-03 明确要求：**写清楚，并给出恢复方法。**

### 5.1 中文（README「安装」段首屏）

> ### 装之前请读这段
>
> Orange 会申请成为电视的主屏幕，并在开机、按主页键、待机唤醒时接管 Home。这需要停用电视自带的桌面，**不同品牌、不同固件的表现不一样**：
>
> - 小米 HyperOS 3 的部分机型：不停用自带桌面（PatchWall）会弹出桌面选择框，必须停用才能接管；
> - 小米 MiTV-MFTR0 / Redmi X65 2026：**停用 `com.mitv.tvhome` 会黑屏**（HOME 落到 FallbackHome，而它也不认第三方桌面）。这条命令不要在这类机器上手敲；
> - TCL / 海信 VIDAA / 创维：各有自己的接管方式，`docs/devices/` 下有逐台实测记录。
>
> **只在你自己负责的设备上装。** 如果这是家里的主力电视，而且你不熟悉 adb，建议先只装、不把它设成默认桌面。
>
> ### 想换回原来的桌面
>
> 不用 adb 的做法：电视「设置 → 应用 → Orange → 清除默认值」，然后按遥控器主页键，选电视自带的桌面。
>
> 要用 adb（更彻底，推荐）：仓库里的 `tools/restore.sh` 就是干这件事的，它会把 Home 还给原桌面、撤销无障碍接管、还原被改过的系统属性：
>
> ```bash
> TV=192.168.1.11 tools/restore.sh          # 归还 Home，保留 Orange 已安装
> TV=192.168.1.11 tools/restore.sh --uninstall   # 顺便卸载
> ```
>
> 如果屏幕已经黑掉、进不去任何界面：`adb connect <电视IP>:5555`，然后
>
> ```bash
> adb shell cmd package set-home-activity --user 0 com.mitv.tvhome/.MainActivityUserMode
> adb shell pm enable com.mitv.tvhome
> adb reboot
> ```
>
> （第二条只是保险——如果之前有人手敲过 `pm disable-user`，需要显式启用回来。）

### 5.2 English（README.en.md 对应段落）

> ### Read this before installing
>
> Orange asks to be the home screen and takes over Home on boot, home-key and wake. On some TVs that means disabling the stock launcher, and **behaviour differs by brand and firmware**. On Xiaomi MiTV-MFTR0 / Redmi X65 2026, disabling `com.mitv.tvhome` **causes a black screen** — do not run that command on those sets. Install only on hardware you are responsible for.
>
> To go back: TV Settings → Apps → Orange → Clear defaults, then press Home and pick the stock launcher. Or, with adb, run `TV=<ip> tools/restore.sh` (`--uninstall` also removes it). If the screen is already black: `adb shell cmd package set-home-activity --user 0 com.mitv.tvhome/.MainActivityUserMode` then `adb reboot`.

### 5.3 Release 说明里的一句话版

> ⚠ 会接管主屏幕。小米 MFTR0 / Redmi X65 2026 上请勿停用 `com.mitv.tvhome`（会黑屏）。换回原桌面：`TV=<ip> tools/restore.sh`，详见 README「安装」段。

## 六、真要公开时的执行清单

按 `docs/process/WORKFLOW.md` 走，这属于 M 档。顺序不要乱：

1. **先补合规**：`LICENSE` 追加自己的版权行并保留 pengchujin 原文；App 内加「开源许可」页（设置 → 关于），列出 MIT 全文。**这条没做完不该对外发。**
2. **门面**：`README.md` / `README.en.md` 重写为 Orange；`site/index.html` 品牌替换；`docs/brand/icon.png` 换成 Orange 自己的图标（现有是上游的）。
3. **截图重拍**：`site/img/home.jpg`、`grid.jpg`、`focus.jpg` 拍的是上游界面，界面里品牌已变，必须重拍，否则图文不符。
4. **建 Release**：`dist/orange-26.4.2.apk` 作为 asset 上传，说明用 5.3 的一句话版 + 三条更新内容。**注意本机没有 `gh`，`release.sh` 的 `gh release create` 那步一直没跑过**，要么装 `gh`，要么网页手动建。
5. **下载镜像**：GitHub Release 链接在国内常打不开，README 同时给 `https://ghproxy.net/<release-url>` 形式的中转链接（`version.json` 里已经在用这个中转，实测可用）。
6. **验证**：换一台没装过的机器（或干净模拟器）从下载页走一遍，确认能装、能设成桌面、能恢复。**这一步不能省——"我本地能装"不等于"别人能装"。**
7. **复盘**：24 小时内写 `docs/retro/<version>.md`。

## 七、当前状态：**什么都没执行**

用户 2026-11-03 23:30 明确「刚刚只是探讨，不是执行」。所以：

- 已经按此**撤销**了探讨论期间做的改动：`release.sh`、`.gitignore`、`docs/NOW.md` 全部还原，
  `dist/*.apk` 与 `dist/shots/` 重新回到版本库跟踪，本地 HEAD 与远端同为 `00f5d3d`。
- **远端没有任何变化**：没推过、没建 Release、没改仓库可见性、没改 README。
- 唯一存在的是这份文档本身（未提交，`git status` 里是一个未跟踪文件）。
- 真要动手那天，从第六节的 7 步清单开始，按 M 档立项。
