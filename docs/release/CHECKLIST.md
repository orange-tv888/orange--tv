# Orange 发布前检查清单

> 状态：**准备中，未授权发布**。自查时间 2026-11-03 23:40。
> 用户指令：「在我确认授权发布前，只允许做正式发布前的准备工作，包括自查，整改，列出清单，校对清单。」

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
| A 法务与许可 | 7 | 3 | 2 | 1 | 1 | 0 |
| B 构建与产物 | 9 | 0 | 0 | 3 | 3 | 3 |
| C 更新通道 | 7 | 2 | 0 | 0 | 4 | 1 |
| D 隐私与联网 | 6 | 2 | 1 | 0 | 2 | 1 |
| E 权限 | 5 | 0 | 0 | 1 | 3 | 1 |
| F 品牌与门面 | 4 | 2 | 0 | 0 | 2 | 0 |
| G 支持与回滚 | 4 | 0 | 0 | 0 | 4 | 0 |
| H 发布动作（授权后才做） | 6 | — | — | — | — | 6 |
| **合计** | **48** | **9** | **3** | **5** | **19** | **12** |

> 这张表由脚本按条目里的实际标记统计得出，不是估的（48 = 9+3+5+19+12）。

**一句话结论**：代码层面基本就绪（构建、签名、更新包的可信校验都在）；**卡住发布的是三件非代码的事** ——
许可合规（A2/A3/A4）、门面仍是上游 Aerial（F1/F2）、以及"手机扫码更新"指向一个 404 页面（C6）。

---

## A 法务与许可

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| A1 | LICENSE 保留上游 MIT 版权行 | `LICENSE` 内 `Copyright (c) 2026 pengchujin` 仍在，且追加了自己的版权行 | ⚠️ 只有上游一行 | `head -5 LICENSE` | 你拍板署名用哪个名字，我改 | 否 |
| A2 | App 内可查开源许可（MIT 要求分发时随附） | 安装后能在设置/关于里看到 MIT 与第三方许可全文 | ⛔ 不存在 | `ls app/src/main/assets` 只有 `aerials.json / cities-cn.tsv / devices.json / web`，无许可文本；全局无"关于/许可"页 | 我（另立 S 档，需编译验证） | **是** |
| A3 | 第三方组件许可清单齐备 | 有 `THIRD-PARTY.md` 列出 Compose/Media3(Apache-2.0)、nanohttpd(BSD)、zxing(Apache-2.0)、Inter(OFL)、Kotlin 等 | ⛔ 不存在 | 依赖见 `app/build.gradle.kts:105-127`；`FONT-LICENSE-OFL.txt` 在仓库根但**未随包** | 我 | **是** |
| A4 | 标明"基于 Aerial (MIT) 修改" | README 或关于页有出处说明 | ⛔ 没有 | `grep Aerial README.md` 只有上游原文，无"衍生自"表述 | 我 | **是** |
| A5 | 素材权属：截图与图标 | 发布用图不含他人商标/桌面截图 | ⚠️ | `site/img/stock-xiaomi.jpg`（小米 PatchWall 截图做对比）、`docs/brand/icon.png`（上游图标，13KB） | 你定是否保留对比图 | 否 |
| A6 | 签名证书主体名 | 证书 DN 不含他人品牌 | ✅ 已确认但**无法改** | `apksigner verify --print-certs` → `CN=JC Aerial, OU=Personal, O=JC, L=Beijing, C=CN`。换证书=签名变更=老用户无法覆盖安装，只能接受 | 记录 | 否 |
| A7 | 包名归属 | `app.orange.tv` 是否为你可主张的标识 | ❓ | 侧载无强制要求；上架应用商店需唯一且不侵权 | 你 | 否 |

## B 构建与产物

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| B1 | 版本号自洽 | `versionCode = 年份*100+Y*10+P` 与 `versionName` 对应 | ✅ | `2642` / `26.4.2`（`app/build.gradle.kts:30-31`） | 我 | 否 |
| B2 | 发布签名（非 debug） | release 构建用 `local/aerial-release.jks` | ✅ | `local/signing.properties` + `aerial-release.jks` 存在；APK 签名 `CN=JC Aerial` | 我 | 否 |
| B3 | 签名密钥有离线备份 | 密钥丢失 = 永远无法更新已装设备 | ❓ | 密钥仅在 `local/`（未入库）。你确认是否另有备份 | **你** | 否（但丢了很贵） |
| B4 | `minSdk 21 / targetSdk 34 / compileSdk 36` 记录在案 | 与声明一致 | ✅ | `app/build.gradle.kts:15,24-25` | — | 否 |
| B5 | 发布哪个 flavor | `standard`（正常 HOME 应用）/ `guard`（无 HOME intent-filter，靠无障碍接管） | ❓ | `app/build.gradle.kts:50-56`。国内部分固件拒绝装声明 CATEGORY_HOME 的包 | **你** | 否 |
| B6 | `lite` 模块（Android 4.x）是否随发布 | 明确"发 / 不发" | ❓ | `lite/` 独立模块，`app.orchard.tv.lite` | **你** | 否 |
| B7 | 全量构建 + lint 通过 | `assembleStandardRelease` 成功且 `lintVital` 无 fatal | ⬜ 本轮未跑 | 配置在（`lint { fatal += "NewApi" }`），但 26.4.2 之后没再全量构建 | 我 | 否 |
| B8 | 三份 APK 哈希一致 | 构建产物 = `update/orange.apk` = `dist/` | ⬜ 需真发版才走 | `release.sh` 已内置该校验 | 我 | 否 |
| B9 | R8 后关键路径可运行 | 混淆/裁剪后首次启动、开机动画、图标加载正常 | ⬜ 未在 release 包上系统性冒烟 | —— | 我 | 否 |

## C 更新通道

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| C1 | 自更新是开着的 | `Updates.canInstall` 返回 true | ✅ | `Updates.kt:215` `PACKAGE = "app.orange.tv"`；`official = APPLICATION_ID == PACKAGE` → true | — | 否 |
| C2 | 更新包校验防篡改 | 包名 + 签名（`sameSigner`）双重校验 | ✅ | `Updates.kt:221,266`；注释 `UpdateSource.kt:18-19` 明确"篡改的 APK 或劫持的 JSON 装不进来" | — | 否 |
| C3 | 更新清单与 APK 在仓库里 | `update/version.json`、`update/orange.apk` 存在且版本对得上 | ✅ | `version.json` 写 `26.4.2`；`orange.apk` 3.1M | — | 否 |
| C4 | 镜像顺序经过实测 | ghproxy.net 优先，其余兜底 | ✅ | `UpdateSource.kt:41` 两个镜像 + 直连 + jsDelivr，注释说明顺序是量出来的 | — | 否 |
| C5 | 仓库必须保持公开 | 私有则更新 404 | ⛔ 这是机制约束 | 更新读 `raw.githubusercontent.com/.../main/update/*`；转私有即断 | **你**（知情确认） | **是（约束）** |
| C6 | "手机扫码更新"的二维码可达 | 扫出来是有效页面 | ⛔ **实测 404** | 二维码目标 `github.com/orange-tv888/orange--tv/releases/latest`（`ControlCenter.kt:377,466`）；API 查 `releases/latest` → **HTTP 404**，因 Releases 为空 | 我（发布时必须同建 Release，或改二维码指向） | **是** |
| C7 | 自更新在 Orange 上真机跑通过 | 点"检查更新"→下载→安装成功 | ⬜ 未验 | 一直用手动 `adb install -r`，自更新路径未在 Orange 上走过 | 我 | 否 |

## D 隐私与联网

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| D1 | 全部外发请求有清单 | 逐条列出目标域与用途 | ✅ 已列 | 更新：ghproxy/raw/jsdelivr；天气：`ipinfo.io`、`api.open-meteo.com`、`geocoding-api.open-meteo.com`；壁纸：`cn.bing.com`；图标：`aerial.qust.me` | — | 否 |
| D2 | 是否向第三方暴露 IP | 有则披露，或去掉 | ⛔ `aerial.qust.me` 是**上游作者的域名** | `IconLibrary.kt:35`。发布后每个 Orange 用户的 IP 都会打到第三方服务器，而那不是你 | **你**（改自建 CDN / 关掉 / 保留） | **是** |
| D3 | `ipinfo.io` 获取公网 IP 用于天气定位 | 有则写进隐私说明 | ⚠️ 会在无提示下外发 IP | `Weather.kt:123` | 我（写披露） | 否 |
| D4 | 无统计/无广告/无账号 | 依赖与代码里无 SDK | ✅ | `app/build.gradle.kts:105-127` 无统计类依赖；全量 URL grep 无上报端点 | — | 否 |
| D5 | 隐私说明文档 | 存在且与实际行为一致 | ⛔ 不存在 | `docs/` 下无隐私文件 | 我 | **是** |
| D6 | 局域网手机配网监听的暴露面 | 仅局域网、可关闭、有鉴权或一次性 | ⬜ 未审 | `PhoneServer`（nanohttpd 2.3.1） | 我 | 否 |

## E 权限

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| E1 | 权限清单完整记录 | 20 项列明用途 | ✅ 已列 | 见下 | — | 否 |
| E2 | 危险权限有正当理由 | 每项能说出用途 | ✅ 大体可解释 | `ACCESS_FINE_LOCATION`（读 Wi-Fi SSID，`SystemState.kt:100-108`）、`QUERY_ALL_PACKAGES`（列应用，启动器必需）、`PACKAGE_USAGE_STATS`/`WRITE_SECURE_SETTINGS`（守护，需 adb 授权）、`KILL_BACKGROUND_PROCESSES`（一键清理） | — | 否 |
| E3 | 老系统权限有上限 | 存储/账号类带 `maxSdkVersion` | ✅ | `READ/WRITE_EXTERNAL_STORAGE maxSdkVersion=23`；`GET_ACCOUNTS`/`AUTHENTICATE_ACCOUNTS maxSdkVersion=22` | — | 否 |
| E4 | `ACCESS_FINE_LOCATION` 是否保留 | 只为显示 Wi-Fi 名称；去掉会退化为不显示 SSID | ❓ | 仅 `SystemState` 读 SSID/RSSI，且已 `runCatching` 包裹 | **你**（隐私观感 vs 功能完整） | 否 |
| E5 | 8 个 `exported="true"` 组件逐个复核 | 每个都有正当外部入口 | ⬜ 未逐个审 | `grep -c exported="true"` = 8 | 我 | 否 |

## F 品牌与门面

| ID | 条目 | 判据 | 现状 | 证据 | 归属 | 阻塞 |
|---|---|---|---|---|---|---|
| F1 | README 描述的是 Orange 而非 Aerial | 标题、简介、下载按钮指向本项目 | ⛔ 全是上游 | `README.md` 标题 `# Aerial`；徽章指向 `pengchujin/aerial-tv/releases/latest` | 我 | **是** |
| F2 | 官网/截图与界面一致 | `site/` 品牌替换，截图重拍 | ⛔ `site/index.html` 36 处上游品牌；截图为旧界面 | `grep -c Aerial site/index.html` = 36 | 我 | **是** |
| F3 | 应用名与图标 | `app_name=Orange`，图标为自有 | ✅ 名称已换，图标待定 | `strings.xml:3` `Orange`；图标见 A5 | — | 否 |
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
| 3 | 我一度以为"转私有不影响更新，因为自更新是关的" | 反过来：自更新开着，所以**转私有一定断更新**。原判断（C5）成立，但理由此前是不牢的 | 由第 1 条连带修正 |
| 4 | 本清单初稿的汇总表：⚠️1 / ❓7 / ✅18 / ⬜13，且 A、B、C、D、F 各组分布也不对 | 实际 **⚠️3 / ❓5 / ✅19 / ⬜12**，各组为 A 7(3/2/1/1/0)、B 9(0/0/3/3/3)、C 7(2/0/0/4/1)、D 6(2/1/0/2/1)、E 5、F 4(2/0/0/2/0)、G 4(4)、H 6 | 用脚本按每行实际标记重新统计，而不是肉眼估 |

**校对结论**：48 条中，**19 条已验、12 条未验、9 条阻塞、5 条待你拍板、3 条待整改**。
12 条未验里，6 条是 H 组（发布动作，本就只能授权后验），另外 6 条（B7、B8、B9、C7、D6、E5）我下一步能补验。
