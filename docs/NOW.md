# 现在在做什么

> 一页看板。每次开工先看这里，收工改这里。**只写当前状态，不写历史**——历史在 `retro/` 和 memory 日志里。

- 更新时间：2026-11-04 21:25
- 线上：**26.5.1**（2651）——修「航拍画面不动」：Apple 撤了 4K 片源，只留 1080p
- 上一版：26.5.0（2650）品牌归位与许可补齐
- 电视：**`192.168.1.11:5555`**（MiTV-ASTP0，今天全程用的这个；旧 192.168.1.8 已失效——之前这里写反了）

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

## 26.5.1：航拍画面不动（Apple 撤了 4K 片源）

用户 13:17 报「选航拍画面的时候它不会动」。查下来**跟这次改版无关，是 Apple 那边把片源撤了**：

| 清单里的字段 | 路径 | 2026-11-04 实测 |
|---|---|---|
| `url`（1080p H.264） | `/Videos/…_2K_AVC.mov` | **114 部全部 200**，第一部 275 MB |
| `hevc`（1080p HEVC） | `/Films/2x/Videos/…` | 全部 404 |
| `uhd`（4K SDR） | `/Films/2x/Videos/…` | 全部 404 |
| `dv`（4K 杜比视界） | `/Films/2x/Videos/…` | 全部 404 |

于是：能解 4K 的电视（这台 amlogic mulan 就是）`Auto` 选到 `Uhd` → 播放器 404 → 重试 →
切 HTTP 再试 → 还是 404 → `next()` 换下一部 → 另一部也是 404 → 转一圈后放弃。
壁纸停在静态帧、屏保停在黑幕，日志里刷满 `wallpaper film failed (ERROR_CODE_IO_BAD_HTTP_STATUS)`。

**改法**：`FilmFormats.HIGHER_RATES_SERVED = false`，`supported()` 只报 1080p；画质选项靠
`qualities.size > 1` 自然不出现，用户看不到一个选了也放不了的 4K。另外 `Screensaver` 遇 404
**直接降一档重载**，不再换片——换片只会换到另一个 404。Apple 哪天恢复，把常量改回 `true` 即可。

真机验证：`cache 0 → 145 MB`、`wallpaper film moving`、404 归零、截图背景全黑（视频层接管，
截屏抓不到 SurfaceView 的视频平面，这是正常的）。

## 仍然不动的一样（动了会出真问题）

- **签名证书 `CN=JC Aerial`**：改 = 换证书 = 所有已装设备永远无法覆盖安装。证书装完不可见，不构成展示性使用。
- **上游 issue 链接**（`pengchujin/aerial-tv/issues/NNN`，中英各 25 处）：他人讨论存档，改了全 404。
- **`FONT-LICENSE-OFL.txt`**：Inter 字体走 OFL，有独立义务，不随品牌清理一起删。

## 发布就绪状态（2026-11-04 21:25 复核）

按 26.5.1 实况把 `docs/release/CHECKLIST.md` 重核了一遍（它此前还停在 26.4.2）。48 条里 **✅ 40、⛔ 1（C5，机制约束）、⚠️ 0、⬜ 7**（其中 6 条是发布动作本身，只有真发版才验得到）。

**就绪的**：许可与署名、品牌、构建与 R8 冒烟、签名与密钥备份、更新通道（四个取源实测 200）、三份 APK 哈希一致（`e1d737ed…`）。

**这一轮补掉的三件事**：
- **D6 局域网监听**：`PhoneServer` 绑 `8790`、只收不发、请求必须带 `/${token}`（SecureRandom 8 位）否则 404。审的时候发现 **D5 隐私说明写错了**——原文说「不打开就不启动」，实际是桌面一启动就 `listen`。已按实际改写。
- **E5 八个导出组件**：7 个有系统权限保护，`RevivalSyncService` 无 permission（任何应用可 bind 到 ISyncAdapter 触发一次同步），但同步动作只是重新登记自己为默认桌面，无外泄无提权，不阻塞。
- **B8 三份哈希**：`update/orange.apk` = `app-standard-release.apk` = `dist/orange-26.5.1.apk`，同 `e1d737ed…`。

**21:20 又补两件**：
- **官网最后一张带旧品牌的截图换掉了**（A5/F2 转 ✅）。旧 `site/img/control-center.jpg` 右下角是 `Aerial 1.65`，还带 NETFLIX 官方 logo；现用 26.5.1 真机截图重制成 1600×900，右下角 `Orange 26.5.1`。这轮 `grep` 全站 html/css/js/json，`Aerial` **0 处**。
- **在电视上实跑了一次「检查更新」**，把 C7 从「完全没验」推进到「检查段已验」：
  `control center started` → 21 秒 → `control center finished: success version=26.5.1`，界面正确显示 `Orange 26.5.1` 且不摆更新条目。**读线上清单 → 解析 → 比版本 → 摆对界面，这四步是真跑过的。**
  仍没走的是 `Updates.install`：下载 APK → `PackageInstaller` 会话 → 装上。

**顺带发现一个体验问题**：首选源 `ghproxy.net` 在电视上**连得上但读不出**（`SocketTimeoutException: timeout`，read 超时 10 秒），整轮检查花 21 秒才回退到直连成功。从 Mac 用 `curl` 打同一个地址是 200 且很快，所以是电视侧的网络差异。已记进下一步候选。

**结论**：代码层面能发。但**「拿到新版之后怎么装上」这条路一次没走过**（C7 下半段），上一版是手动 adb 装上去的，恰好把这段绕过去了。

## 下一步候选

| 候选 | 档 | 状态 |
|---|---|---|
| 真机验自更新**下半段**（下载+安装），收掉 C7 | S | **检查段 21:18 已验通**（见下）。剩下「下载 APK → PackageInstaller 安装」没走过；等下一版（26.5.2）发出去后**别用 adb，改用电视上的「检查更新」装** |
| 真机验 26.4.2 → 26.5.0 这一步的 HOME 绑定（组件名变了） | S | 已验（2026-11-04）：**没丢**，但只在原桌面已停用的机器上成立，见已知隐患 |
| 控制中心加「当前不是默认桌面」的提示，让升级后丢了绑定的人不用跑脚本 | M | 未立项 |
| 更新检查慢：首选源 `ghproxy.net` 在电视上读超时 10s，整轮 21s | S | 未立项。2026-11-04 21:18 实测：连上但读不出（`SocketTimeoutException: timeout`），回退到直连才成功。`Updates.kt:152-153` 是 connect 8s / read 10s |
| `tools/dev.sh`（构建/装机/检查一条命令） | M | 未立项 |
| 更新包体积（每次发版仓库 +3MB） | M | 未立项，只是隐患 |

## 阻塞

- 无。（C5「仓库保持公开」是机制约束，不是待修项）

## 已知隐患

- **覆盖升级的 HOME 绑定只在「唯一候选」时安全**（2026-11-04 实测，结论带条件）：
  组件名从 `app.orange.tv/app.orchard.tv.MainActivity` 变成 `app.orange.tv/app.orange.tv.MainActivity`，
  `set-home-activity` 记的是旧名，升级后那条绑定指向一个不存在的组件。
  本机电视从 26.4.2 真升上来**没丢**，但查下来成立的前提是：`com.mitv.tvhome` 与 `com.android.tv.settings`
  早已 `disable-user`（见「开机直达方案」），`cmd package query-activities -a MAIN -c HOME` 只回
  `app.orange.tv` 一个候选。**系统没得挑，所以自动落到新组件名，不是改名无害。**
  没停用原桌面的电视上，多候选 + 旧绑定失效 → 会弹选择框或退回原桌面；兜底是 `TV=<ip> tools/install.sh` 重跑。
- **签名密钥已备份（2026-11-04）**：加密磁盘映像在**仓库外**的 `key-backup/orange-keys-20261104.dmg`（AES-256，口令单独保管）。
  证书 SHA-256 `AE:94:9B:3E…8D:7D:0B`，RSA 2048，有效期至 2056-10-23。已做过“解密→重签→验指纹”演练。
  **还需要你做一步：把 dmg 拷到第二个介质（U 盘/另一台机器）**。
- **画质只剩 1080p**：Apple 撤了 `/Films/2x/` 下所有高码率文件（4K SDR / 4K 杜比视界 / 1080p HEVC），
  只剩 2K AVC。所以现在所有电视都放 1080p，界面也不再提供画质选择。Apple 若恢复，把
  `FilmFormats.HIGHER_RATES_SERVED` 改回 `true`。
- ~~**`release.sh` 第 11 步还有 `gh release create`**~~ → **2026-11-04 21:15 已改**：这一步默认跳过，要建需显式加 `--release`。理由写进脚本注释：更新读 raw，Release 页面只会在仓库首页挂一个公开下载口，正是"不对外发布"不要的那件事。
- 本机 SSH 22 端口到 GitHub 常年不通，push 走 443（`release.sh` 已内置兜底）。
- 每次发版 `update/orange.apk` 覆盖写，仓库历史里会累计约 3MB/版。
- `docs/devices/` 里的实测日志仍写着旧的 `app.orchard.tv` 组件名——**故意的**，那是当时的真实 dumpsys 输出，改了等于伪造证据。26.5.0 起的记录按新包名写。
