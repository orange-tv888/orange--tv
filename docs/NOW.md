# 现在在做什么

> 一页看板。每次开工先看这里，收工改这里。**只写当前状态，不写历史**——历史在 `retro/` 和 memory 日志里。

- 更新时间：2026-11-04 13:10
- 当前版本：**26.5.0**（versionCode 2650）——品牌归位与许可补齐（已改号，待推送）
- 电视：**`192.168.1.8:5555`**（旧 192.168.1.11 已失效）

## 更新通道健康

用户 2026-11-04 的要求是两件事，别混：**① 不对外发布软件 ② 更新必须照常推进。**

| 项 | 状态 |
|---|---|
| 仓库公开（更新前提） | `private: false` —— **转私有 = 更新立刻 404，这条是机制不是配置** |
| `update/version.json` | 指向 `update/orange.apk` |
| 三个取源 | ghproxy.net / raw / jsdelivr |

## 进行中

`26.5.0-rebrand` → **已完成**。用户 2026-11-04 决定：既然 MIT 允许改名，就把上游品牌全部换掉、同时把 MIT 要求的署名补齐。

做了三件事：

1. **删上游品牌**：`namespace` 由 `app.orchard.tv` 迁到 `app.orange.tv`（109 个源文件、`app/` 与 `lite/` 源码树、6 个 tools 脚本、`baseline-prof.txt`）；README 与 `site/` 的上游域名、下载链接、反馈入口全部改到本仓库；图标渐变由绿紫改橙。
2. **补 MIT 署名**（这一步不做就是违约）：`LICENSE` 加自己的版权行；新增应用内「控制中心 → 关于 · 开源许可」，MIT 全文可逐行滚动；`THIRD-PARTY.md` 列全 13 项运行期依赖；README 加「来源」段。
3. **顺带修掉的三个真问题**：`install.sh` 原本从 `aerial.qust.me` 下载**上游 APK**（照 README 装会装成别人的应用）；`lite` 的共享源码路径写死旧包名导致编译失败；扫码更新指向一个 404 的 `releases/latest`。

另外关掉了远程图标库（`IconLibrary.ENABLED = false`）——那域名属上游作者，开着等于把每台 Orange 的 IP 与安装列表发给别人。磁贴回退到应用自身图标。

### 第二轮清理（同批，11-04 晚）

第一轮漏的是「搜 `Aerial` 搜不到的那一层」，按用户实际会看到的顺序重过了一遍：

- **6 个 tools 脚本** 90 余处英文提示、`aerial.qust.me` 链接、`aerial-report-*.md` 输出名、`wait_aerial`/`restart_aerial` 函数名；`get.sh` 去掉已不存在的下载站，改本仓库 raw + jsDelivr。
- **19 个日志 tag** `Aerial*` → `Orange*`，并把 `tools/report.sh` 的 grep 正则与 `sed` 锚点同步改掉，否则诊断抓不到应用日志。
- **TV banner** `drawable-{xhdpi,xxhdpi}/banner.png` 一直是**绿紫渐变 + "Aerial" 字标**，在电视的应用列表里直接可见；已用 Pillow 重绘为橙色 + "Orange"。
- **内置手机网页** 9 处指代本应用的文案，以及那条依赖诊断标题行的版本解析正则。
- **69 个文件 444 处源码注释** 里的 `Aerial`。
- **修掉一个真 bug**：`install.sh` 的 `guard_bound()` 用 `label=(Aerial|app\.orchard\.tv)` 判断守护是否绑定，真机打出的是 `label=app.orange.tv.guard.Ho…`，两个分支都匹配不上，导致脚本每次安装都白跑一遍开守护流程。第一轮没发现是因为这处包名写成转义形式，`grep "app\.orchard"` 搜不到。

清单见 `docs/release/CHECKLIST.md`：48 条，**⛔ 从 9 降到 1**（只剩 C5 那个机制约束），✅ 26 → 35。

## 第三轮：原先打算留下的四样也改了（用户 12:13 拍板）

「4 样没有动一并动了，装机实测，没问题再推送发版。目前老设备就一台，就是我们这台电视。」于是每一处都按「改名 + 旧值兼容」做：

| 原打算不动 | 现在 |
|---|---|
| `dream/AerialDream` | `dream/OrangeDream`。组件名变了 → 屏保要重设一次，`tools/install.sh` 每次安装都会重写 `screensaver_components`，跑一遍即恢复 |
| `aerial_vt_home_pkg` | `orange_vt_home_pkg`。`install.sh` 写新键删旧键，`restore.sh` 两个键都读 |
| `Account("Aerial")` | 新建账户名 `Orange`；账户按 type 查，旧名账户直接复用，不用迁移 |
| `Aerial`/`AerialSet`/`AerialStream`/`AerialQuality`/`AerialFormats`/`Aerials` | `Film`/`FilmSet`/`FilmStream`/`FilmQuality`/`FilmFormats`/`Films`，文件一并 `git mv`，`assets/aerials.json` → `films.json` |

同时带迁移或兼容的持久化名字：prefs `aerial_set`→`orange_set`（整个旧文件逐键复制）、prefs `aerial`→`orange`（亮度，读旧写新）、键 `aerial_quality`→`film_quality`（先新后旧）、PIN 盐（两个盐都试，已设 PIN 的电视不会被锁在外面）、缓存目录与日志/更新临时文件名（可直接重建）。

现在全仓 `grep -ri aerial` 只剩三种：英文普通名词「航拍」（`aerial films`、资源名 `ss_aerial`）、音效枚举 key `"aerial"`、以及迁移代码里写明的旧名字（不写就迁不动）。

## 仍然不动的一样（动了会出真问题）

- **签名证书 `CN=JC Aerial`**：改 = 换证书 = 所有已装设备永远无法覆盖安装。证书装完不可见，不构成展示性使用。
- **上游 issue 链接**（`pengchujin/aerial-tv/issues/NNN`，中英各 25 处）：他人讨论存档，改了全 404。
- **`FONT-LICENSE-OFL.txt`**：Inter 字体走 OFL，有独立义务，不随品牌清理一起删。

## 下一步候选

| 候选 | 档 | 状态 |
|---|---|---|
| 真机验自更新（26.4.2 → 26.5.0），顺带验掉 C7 与 B8，并看 HOME 绑定会不会丢 | S | 发版后立刻做 |
| `site/img/control-center.jpg` 重拍（那张里能看到 "Aerial 1.65"，其余 12 张无品牌字样） | S | 不发官网就不急 |
| 控制中心加「当前不是默认桌面」的提示，让升级后丢了绑定的人不用跑脚本 | M | 未立项 |
| D6 局域网监听暴露面、E5 八个导出组件逐个复核 | M | 未审 |
| `tools/dev.sh`（构建/装机/检查一条命令） | M | 未立项 |
| 更新包体积（每次发版仓库 +3MB） | M | 未立项，只是隐患 |

## 阻塞

- 无。（C5「仓库保持公开」是机制约束，不是待修项）

## 已知隐患

- **覆盖升级会丢一次 HOME 绑定**：组件名从 `app.orange.tv/app.orchard.tv.MainActivity` 变成
  `app.orange.tv/app.orange.tv.MainActivity`，老设备上 `set-home-activity` 记的是旧名，升级后那条绑定指向一个不存在的组件。
  本机电视是重设过之后才测的，所以现在看着正常；**「从 26.4.2 真升上来会怎样」还没实测**，发版前必须验一次。
  兜底是 `TV=<ip> tools/install.sh` 重跑一遍。
- **签名密钥已备份（2026-11-04）**：加密磁盘映像在**仓库外**的 `key-backup/orange-keys-20261104.dmg`（AES-256，口令单独保管）。
  证书 SHA-256 `AE:94:9B:3E…8D:7D:0B`，RSA 2048，有效期至 2056-10-23。已做过“解密→重签→验指纹”演练。
  **还需要你做一步：把 dmg 拷到第二个介质（U 盘/另一台机器）**。
- **`release.sh` 第 11 步还有 `gh release create`**：本机没装 `gh` 所以从没跑过，但哪天装了，发版就会自动挂出一个公开下载页——和"不对外发布"冲突。等用户决定删还是留。
- 本机 SSH 22 端口到 GitHub 常年不通，push 走 443（`release.sh` 已内置兜底）。
- 每次发版 `update/orange.apk` 覆盖写，仓库历史里会累计约 3MB/版。
- `docs/devices/` 里的实测日志仍写着旧的 `app.orchard.tv` 组件名——**故意的**，那是当时的真实 dumpsys 输出，改了等于伪造证据。26.5.0 起的记录按新包名写。
