# Orange 发布前检查清单

> 状态：**准备中，未授权发布**。自查 2026-11-03 23:40，五条拍板与执行 2026-11-04 09:20。
> 用户指令：「在我确认授权发布前，只允许做正式发布前的准备工作，包括自查，整改，列出清单，校对清单。」
> 2026-11-04 11:20：**品牌归位与许可补齐完成**（`26.5.0-rebrand`）—— A2/A3/A4 补齐、F1/F2 改完、D2 关掉上游图标库、
> D5 隐私说明落地、C6 二维码改指仓库。⛔ 由 9 条降到 1 条（只剩 C5 这个机制约束），✅ 由 26 升到 35。

## 怎么读这张表

状态只有五种，看到 ⛔ 就是"不解决不能发"：

| 记号 | 含义 |
|---|---|
| ✅ | 已实际验证通过，右侧有证据 |
| ⚠️ | 需整改，能修 |
| ❓ | 需你拍板，我不替你决定 |
| ⛔ | 阻塞发布 |
| ⬜ | 未验（条件不具备，例如"必须真发一次版才验得到"） |

**归属**：`我` = 我能改；`你` = 只能你决定（含"要不要这么做"）。

## 一、汇总

| 类别 | 条数 | ⛔ 阻塞 | ⚠️ 待改 | ❓ 待你定 | ✅ 已通过 | ⬜ 未验 |
|---|---|---|---|---|---|---|
| A 法务与许可 | 7 | 0 | 1 | 0 | 6 | 0 |
| B 构建与产物 | 9 | 0 | 0 | 0 | 8 | 1 |
| C 更新通道 | 7 | 1 | 0 | 0 | 5 | 1 |
| D 隐私与联网 | 6 | 0 | 0 | 0 | 5 | 1 |
| E 权限 | 5 | 0 | 0 | 0 | 4 | 1 |
| F 品牌与门面 | 4 | 0 | 1 | 0 | 3 | 0 |
| G 支持与回滚 | 4 | 0 | 0 | 0 | 4 | 0 |
| H 发布动作（授权后才做） | 6 | — | — | — | — | 6 |
| **合计** | **48** | **1** | **2** | **0** | **35** | **10** |

> 这张表由脚本按条目里的实际标记统计得出，不是估的（48 = 1+2+0+35+10）。

**一句话结论**：许可合规与品牌门面已做完（A 段 6/7、F 段 3/4），代码层面就绪。
**剩下的唯一 ⛔ 是 C5**：仓库必须保持公开——这是更新机制的约束，不是待修项，你已知情。
两条 ⚠️ 分别是 A5（截图待重拍）与 F2（官网截图，不发官网就不阻塞）。
四条 ⬜ 里 B8 与 C7 这次发版能验掉，D6/E5 是没来得及逐条审的代码复核。

---

## A 法务与许可

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| A1 | LICENSE 保留上游 MIT 版权行 | `LICENSE` 内 `Copyright (c) 2026 pengchujin` 仍在，且追加了自己的版权行 | ✅ 两行 | `head -5 LICENSE` → `pengchujin` + `JC Aerial — modifications in Orange`（2026-11-04 按拍板用 `JC Aerial`） | 完成 | 否 |
| A2 | App 内可查开源许可（MIT 要求分发时随附） | 安装后能在设置/关于里看到 MIT 与第三方许可全文 | ✅ 控制中心 → 关于 · 开源许可 | `ControlCenter.AboutPage`；`unzip -l` 见 `assets/licences/{LICENSE.txt,THIRD-PARTY.md,FONT-LICENSE-OFL.txt}`；真机截图 `docs/qa/26.5.0-rebrand.md` | 完成 | 否 |
| A3 | 第三方组件许可清单齐备 | 有 `THIRD-PARTY.md` 列出 Compose/Media3(Apache-2.0)、nanohttpd(BSD)、zxing(Apache-2.0)、Inter(OFL)、Kotlin 等 | ✅ 仓库根 `THIRD-PARTY.md`，并随包 | 13 条运行期依赖 + 2 条仅测试；由 `app/build.gradle.kts` 的 `copyLicenceAssets` 复制进 assets，改一处即同步 | 完成 | 否 |
| A4 | 标明"基于 Aerial (MIT) 修改" | README 或关于页有出处说明 | ✅ README「许可」段 + 应用内关于页首段 | `README.md`「**来源**」；`values*/strings.xml` 的 `cc_about_origin`（五语） | 完成 | 否 |
| A5 | 素材权属：截图与图标 | 发布用图不含他人商标/桌面截图 | ⚠️ 图标与 TV banner 已重绘，官网截图待重拍 | 图标：`ic_launcher_background.xml` 与 `docs/brand/icon.svg` 由绿紫改为橙 `#FFA733→#FF5E1A`，5 个 mipmap PNG 重渲染；TV banner `drawable-{xhdpi,xxhdpi}/banner.png` 原为绿紫渐变 + "Aerial" 字标，已重绘为橙 + "Orange"；**`site/img/control-center.jpg` 里能直接看到 "Aerial 1.65"**，属 F2 待重拍 | 我 | 否 |
| A6 | 签名证书主体名 | 证书 DN 不含他人品牌 | ✅ 已确认但**无法改** | `apksigner verify --print-certs` → `CN=JC Aerial, OU=Personal, O=JC, L=Beijing, C=CN`。换证书=签名变更=老用户无法覆盖安装，只能接受；证书装完不可见，不构成展示性使用 | 记录 | 否 |
| A7 | 包名归属 | `applicationId` 与 `namespace` 一致 | ✅ 已统一 | **2026-11-04 改判**：`applicationId` 仍为 `app.orange.tv`（不动，动了就是另一个应用）；`namespace` 由 `app.orchard.tv` 迁到 `app.orange.tv`，109 个源文件与 `app/`、`lite/` 源码树同步移动。组件名变为 `app.orange.tv/app.orange.tv.MainActivity`；签名、覆盖安装、更新通道不受影响 | 完成 | 否 |

## B 构建与产物

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| B1 | 版本号自洽 | `versionCode = 年份*100+Y*10+P` 与 `versionName` 对应 | ✅ | `2642` / `26.4.2`（`app/build.gradle.kts:30-31`） | 我 | 否 |
| B2 | 发布签名（非 debug） | release 构建用 `local/aerial-release.jks` | ✅ | `local/signing.properties` + `aerial-release.jks` 存在；APK 签名 `CN=JC Aerial` | 我 | 否 |
| B3 | 签名密钥有离线备份 | 密钥丢失 = 永远无法更新已装设备 | ✅ 已备份并演练 | 2026-11-04：`key-backup/orange-keys-20261104.dmg`（AES-256 加密，仓库外，140KB）；证书 SHA-256 `AE:94:9B:3E…8D:7D:0B`、RSA 2048、有效期至 2056-10-23。**已验**：从备份解密 → 用 `apksigner` 重签 APK → 指纹仍为 `ae949b3e…d18d7d0b` | 我（已完成） | 否 |
| B4 | `minSdk 21 / targetSdk 34 / compileSdk 36` 记录在案 | 与声明一致 | ✅ | `app/build.gradle.kts:15,24-25` | — | 否 |
| B5 | 发布哪个 flavor | `standard`（正常 HOME 应用）/ `guard`（无 HOME intent-filter，靠无障碍接管） | ✅ 只发 standard | **决定 2026-11-04：只发 `standard`。** 两者 `applicationId` 相同，存量设备跑的是 standard，换 flavor = 把“系统认可的桌面”改成“无障碍抢焦点”，纯风险零收益。`guard` 仅在某台固件拒装 CATEGORY_HOME 时单独打（带 `-guard` 后缀） | 已定 | 否 |
| B6 | `lite` 模块（Android 4.x）是否随发布 | 明确"发 / 不发" | ✅ 不发 | **决定 2026-11-04：不发。** `lite` 是 `minSdk 14` 的独立应用（`app.orchard.tv.lite`，停在 1.11/12），**没接更新通道，发出去也推不了更新**；用户里没有 4.x 设备。代码保留不删，`release.sh` 只打 `assembleStandardRelease`（现状已如此） | 已定 | 否 |
| B7 | 全量构建 + lint 通过 | `assembleStandardRelease` 成功且 `lintVital` 无 fatal | ✅ 2026-11-04 通过 | 16:36 `BUILD SUCCESSFUL in 22m 03s`（52 tasks，含 `lintVitalAnalyzeStandardRelease` / `lintVitalStandardRelease` / `minifyStandardReleaseWithR8`），无 fatal | 我 | 否 |
| B8 | 三份 APK 哈希一致 | 构建产物 = `update/orange.apk` = `dist/` | ⬜ 需真发版才走 | `release.sh` 已内置该校验 | 我 | 否 |
| B9 | R8 后关键路径可运行 | 混淆/裁剪后首次启动、开机动画、图标加载正常 | ✅ 2026-11-04 装机验过 | 把 R8 产物 `install -r` 到 MiTV-ASTP0（`192.168.1.8`）→ 启动到桌面 → `logcat -b crash` 空、`mResumedActivity` 是 Orange、截图渲染正常（顶栏 WiFi/电池图标在、应用行、毛玻璃正常） | 我 | 否 |

## C 更新通道

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| C1 | 自更新是开着的 | `Updates.canInstall` 返回 true | ✅ | `Updates.kt:215` `PACKAGE = "app.orange.tv"`；`official = APPLICATION_ID == PACKAGE` → true | — | 否 |
| C2 | 更新包校验防篡改 | 包名 + 签名（`sameSigner`）双重校验 | ✅ | `Updates.kt:221,266`；注释 `UpdateSource.kt:18-19` 明确"篡改的 APK 或劫持的 JSON 装不进来" | — | 否 |
| C3 | 更新清单与 APK 在仓库里 | `update/version.json`、`update/orange.apk` 存在且版本对得上 | ✅ | `version.json` 写 `26.4.2`；`orange.apk` 3.1M | — | 否 |
| C4 | 镜像顺序经过实测 | ghproxy.net 优先，其余兜底 | ✅ | `UpdateSource.kt:41` 两个镜像 + 直连 + jsDelivr，注释说明顺序是量出来的 | — | 否 |
| C5 | 仓库必须保持公开 | 私有则更新 404 | ⛔ 这是机制约束 | 更新读 `raw.githubusercontent.com/.../main/update/*`；转私有即断 | **你**（知情确认） | **是（约束）** |
| C6 | "手机扫码更新"的二维码可达 | 扫出来是有效页面 | ✅ 已改指向仓库主页 | 原来指 `releases/latest`，API 实测 **HTTP 404**（Releases 为空）；2026-11-04 改为 `github.com/orange-tv888/orange--tv`，仓库页带安装命令与 APK 路径 | 完成 | 否 |
| C7 | 自更新在 Orange 上真机跑通过 | 点"检查更新"→下载→安装成功 | ⬜ 未验 | 一直用手动 `adb install -r`，自更新路径未在 Orange 上走过 | 我 | 否 |

## D 隐私与联网

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| D1 | 全部外发请求有清单 | 逐条列出目标域与用途 | ✅ 已列 | 更新：ghproxy/gh-proxy/raw/jsdelivr；天气：`ipinfo.io`、`api.open-meteo.com`、`geocoding-api.open-meteo.com`；壁纸：`cn.bing.com`；影片：`sylvan.apple.com`；图标：~~`aerial.qust.me`~~（已关）。逐条见 `docs/PRIVACY.md` | 完成 | 否 |
| D2 | 是否向第三方暴露 IP | 有则披露，或去掉 | ✅ 已关掉 | `IconLibrary.ENABLED = false`（26.5.0）。该域名属上游作者，开着会让每台 Orange 的 IP 与安装列表打到别人的服务器。磁贴回退到应用自身图标；将来把 `BASE` 换成自建 CDN 再打开 `ENABLED` 即可 | 完成 | 否 |
| D3 | `ipinfo.io` 获取公网 IP 用于天气定位 | 有则写进隐私说明 | ✅ 已披露 | `docs/PRIVACY.md` 第二节：目标域、发出什么、何时、能否关；并说明只到城市级、坐标只存本机 | 完成 | 否 |
| D4 | 无统计/无广告/无账号 | 依赖与代码里无 SDK | ✅ | `app/build.gradle.kts:105-127` 无统计类依赖；全量 URL grep 无上报端点 | — | 否 |
| D5 | 隐私说明文档 | 存在且与实际行为一致 | ✅ `docs/PRIVACY.md` | 六节：不收集什么、联网逐条、数据存哪、权限用途、第三方、改动方式；按 `26.5.0` 核对 | 完成 | 否 |
| D6 | 局域网手机配网监听的暴露面 | 仅局域网、可关闭、有鉴权或一次性 | ⬜ 未审 | `PhoneServer`（nanohttpd 2.3.1）；隐私说明已写明「只在你自己的局域网内、不经过互联网、不打开就不启动」，代码层未逐个审 | 我 | 否 |

## E 权限

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| E1 | 权限清单完整记录 | 20 项列明用途 | ✅ 已列 | 见下 | — | 否 |
| E2 | 危险权限有正当理由 | 每项能说出用途 | ✅ 可解释 | `QUERY_ALL_PACKAGES`（列应用，启动器必需）、`PACKAGE_USAGE_STATS`/`WRITE_SECURE_SETTINGS`（守护，需 adb 授权）、`KILL_BACKGROUND_PROCESSES`（一键清理）、`REQUEST_INSTALL_PACKAGES`（控制中心更新）。定位已删（见 E4），现在全表没有一项位置/通讯录/短信类权限 | — | 否 |
| E3 | 老系统权限有上限 | 存储/账号类带 `maxSdkVersion` | ✅ | `READ/WRITE_EXTERNAL_STORAGE maxSdkVersion=23`；`GET_ACCOUNTS`/`AUTHENTICATE_ACCOUNTS maxSdkVersion=22` | — | 否 |
| E4 | `ACCESS_FINE_LOCATION` 是否保留 | 只为显示 Wi-Fi 名称；去掉会退化为不显示 SSID | ✅ 已删除 | **决定 2026-11-04：删。** 关键事实：它由 `tools/install.sh` 用 adb 授予，**侧载安装的用户本来就没这个权限**，SSID 早已显示不出；`SystemState` 对 `<unknown ssid>` 已有过滤，删了不崩。已删 `AndroidManifest.xml` 声明 + `install.sh` 的 grant 行 + `Diagnostics.kt` 的 `location=` 字段；`ACCESS_WIFI_STATE` 保留（信号格要用）。代价：跑过 install.sh 的机器（含本机这台）不再显示 Wi-Fi 名称 | 已做 | 否 |
| E5 | 8 个 `exported="true"` 组件逐个复核 | 每个都有正当外部入口 | ⬜ 未逐个审 | `grep -c exported="true"` = 8 | 我 | 否 |

## F 品牌与门面

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| F1 | README 描述的是 Orange 而非 Aerial | 标题、简介、下载按钮指向本项目 | ✅ 标题 `# Orange`，下载指向 `update/orange.apk` | 全文 `Aerial` 0 处；上游 `aerial.qust.me` 0 处；下载/安装/反馈入口全部改为本仓库；末尾新增「来源」段履行署名 | 完成 | 否 |
| F2 | 官网/截图与界面一致 | `site/` 品牌替换，截图重拍 | ⚠️ 品牌已换，截图未重拍 | `site/` 内 `Aerial` 仅剩组件名 `AerialDream`（正确保留）；`aerial.qust.me` 0 处、`pengchujin` 0 处；13 张截图里 `control-center.jpg` 拍到旧版界面右下角的 "Aerial 1.65"，`home.jpg` 等其余图逐张看过、无品牌字样。站点当前不对外发布，故不阻塞 | 我（需真机重拍） | 否 |
| F3 | 应用名与图标 | `app_name=Orange`，图标为自有 | ✅ 名称已换，图标已改橙 | `strings.xml:3` `Orange`；图标渐变改 `#FFA733→#FF5E1A`，见 A5 | 完成 | 否 |
| F4 | 更新说明模板 | 三条以内、说人话、不含内部术语 | ✅ | `26.4.2` 的 `version.json` 三条即为范例 | — | 否 |

## G 支持与回滚

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| G1 | 有"换回原桌面"的一键脚本 | 存在且覆盖小米/海信/TCL 的还原 | ✅ | `tools/restore.sh`（71 行）：还 Home、撤无障碍、还原 `persist.vt.use_home_pkg`、`--uninstall` | — | 否 |
| G2 | 风险披露文案 | 中英双语，含无 adb / 有 adb / 已黑屏三档 | ✅ | `docs/process/DISTRIBUTION.md` 第五节（该文件尚未提交） | — | 否 |
| G3 | 机型差异警告 | 明确"某些小米机型停用桌面会黑屏" | ✅ | `docs/devices/xiaomi-mitv-mftr0.md:17` 实测记录 | — | 否 |
| G4 | 兼容设备矩阵 | 逐台记录可用/坑 | ✅ | `docs/devices/`（含 tvguo-6、skyworth-p3-box、tcl-m7221-cn 等） | — | 否 |

## H 发布动作（**授权后才做，现在一条都不执行**）

| ID | 条目 | 判据 |
|---|---|---|
| H1 | 版本号递增并全量构建 | `release.sh <ver> "说明"…` 一次跑通 |
| H2 | 三份 APK 哈希一致 | 脚本内置校验通过 |
| H3 | 新电视安装包重打 | `dist/orange-newtv-setup.zip` 内 `payload/orange.apk` 为新版 |
| H4 | 中转可读到新版 | `ghproxy.net` 中转返回新版本号 |
| H5 | 建 Release（含 APK + 说明 + 披露） | 页面可达，且 C6 的二维码随之有效 |
| H6 | 真机从下载页装一遍 | 换一台干净设备，装、设桌面、能恢复 |

---

## 附：本轮证据（可复现）

```bash
# 签名（不是 debug 密钥）
apksigner verify --print-certs dist/orange-26.4.2.apk
# → CN=JC Aerial, OU=Personal, O=JC, L=Beijing, C=CN
# → SHA-256 ae949b3ea371a2ae223642d42de658539a49b1bdd2ba8f6ad55bc706d18d7d0b

# 自更新开着
grep -n 'PACKAGE = ' app/src/main/java/app/orchard/tv/update/Updates.kt   # → app.orange.tv

# 手机扫码更新指向 404
curl -s -o /dev/null -w '%{http_code}' https://api.github.com/repos/orange-tv888/orange--tv/releases/latest
# → 404

# 包里没有许可文本
ls app/src/main/assets   # → aerials.json cities-cn.tsv devices.json web

# 图标库指向第三方域名
grep -rn 'aerial.qust.me' app/src/main/java   # → IconLibrary.kt:35
```

## 校对记录（本轮实际改正的错误）

| # | 原先的说法 | 校对后的实情 | 怎么发现的 |
|---|---|---|---|
| 1 | 我的项目记忆写着「`Updates.PACKAGE` 保持 `app.orchard.tv`，作为禁用自更新的判据」 | **错**。代码是 `PACKAGE = "app.orange.tv"`，注释写明"Orange 发布自己的版本"，`canInstall` 为 **true** —— 自更新是开着的 | 直接读 `Updates.kt:209-221`，而不是信记忆 |
| 2 | 差点把 `ACCESS_FINE_LOCATION` 判为"白声明、可删" | 实际用于读 Wi-Fi SSID 显示网络名（`SystemState.kt:100-108`），删了功能会退化 | grep 到 `WifiManager` 才没误判 |
| 2b | 上一条当时判定「删了功能会退化」，所以保留 | **只对了一半。** 它确实只服务于 SSID，但**侧载用户从来没被授予过这个权限**（只有 `install.sh` 用 adb grant），所以对大多数人删不删都一样。最终仍删（见 E4） | 读到 `AndroidManifest.xml` 那句注释「Granted over adb by tools/install.sh, never prompted」才发现 |
| 3 | 我一度以为"转私有不影响更新，因为自更新是关的" | 反过来：自更新开着，所以**转私有一定断更新**。原判断（C5）成立，但理由此前是不牢的 | 由第 1 条连带修正 |
| 4 | 本清单初稿的汇总表：⚠️1 / ❓7 / ✅18 / ⬜13，且 A、B、C、D、F 各组分布也不对 | 实际 **⚠️3 / ❓5 / ✅19 / ⬜12**，各组为 A 7(3/2/1/1/0)、B 9(0/0/3/3/3)、C 7(2/0/0/4/1)、D 6(2/1/0/2/1)、E 5、F 4(2/0/0/2/0)、G 4(4)、H 6 | 用脚本按每行实际标记重新统计，而不是肉眼估 |

**校对结论**：48 条中，**26 条已验、10 条未验、9 条阻塞、3 条待整改、0 条待拍板**。
10 条未验里，6 条是 H 组（发布动作，本就只能授权后验），剩下 4 条（B8 三份哈希、C7 自更新真机、D6 局域网监听、E5 导出组件）。

---

## 五条拍板与执行（2026-11-04 09:10）

用户 2026-11-04：「五条按你建议的去执行」。五条全部采纳，执行情况如下。
**注意：这五条都不是"发布动作"，做完仍然没有发布任何东西。**

| # | 结论 | 实际动作 | 证据 |
|---|---|---|---|
| A7 包名 | 保留 `app.orange.tv` | 不改代码 | 改 `applicationId` 会让已装设备（含本机电视）永远收不到更新，这是硬约束 |
| B3 密钥备份 | 今天必须做 | 生成 `key-backup/orange-keys-20261104.dmg`（AES-256，仓库外） | 解密 → 重签 APK → `apksigner verify` 指纹 `ae949b3e…d18d7d0b` 与原件一致；jks SHA-1 `8f0b17ab…` |
| B5 flavor | 只发 `standard` | 不改代码 | 两者同包名，存量跑 standard，换 guard = 把系统桌面换成无障碍接管 |
| B6 lite | 不发 | 不改代码 | `minSdk 14`、停在 1.11、**没接更新通道**，发出去也推不了更新 |
| E4 定位权限 | 删除 | 改 3 处：`AndroidManifest.xml` 删声明与 gps uses-feature、`tools/install.sh` 删 grant 行、`Diagnostics.kt` 删 `location=` 字段 | 编译通过 + 合并后清单与打包产物里 `aapt dump permissions` 无该权限（见下） |

### E4 的验证证据

```
$ aapt dump permissions app/build/outputs/apk/standard/release/app-standard-release.apk
# → 期望：不出现 ACCESS_FINE_LOCATION
```

删了不影响功能，因为 `SystemState` 对取不到 SSID 的情况本来就有过滤（`<unknown ssid>` / 空串），
信号强度走 `ACCESS_WIFI_STATE`，与定位无关。**代价只有一条**：跑过 `install.sh` 的机器不再显示 Wi-Fi 名称。

### 还没做的（不在五条里）

- B8 三份哈希一致 —— 只有走 `release.sh` 才会产生，下次发版验
- C7 自更新真机验证 —— 需要先有一个“比 26.4.2 新”的版本可控可测；电视已连上（`192.168.1.8`），但没版本可升级，所以这次验不了
- D6 局域网监听暴露面、E5 八个导出组件逐个复核 —— 可补

### 本次顺带验掉的（原先标未验）

- **B7 全量构建 + lint**：2026-11-04 `BUILD SUCCESSFUL in 22m 03s`
- **B9 R8 后冒烟**：R8 产物已装到 `192.168.1.8` 并跑起来（无崩溃、渲染正常）

> ❗副作用：这台电视现在跑的构建是“26.4.2 + 删定位权限”，与 `update/orange.apk`（旧 26.4.2）不同。
> 因为版本号相同，电视不会提示更新；**下次真发版时会重新构建并覆盖，自然对齐**。
