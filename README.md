# Librera Classic

把一台 **Sony Xperia XZs（SO-03J，docomo 日版）** 改成 iPod Classic 形态的「本地音乐 + 电子书」单包固件：一个包名、一个桌面、一次安装，音乐与阅读共用同一个转盘。

A single-APK retrofit that turns a **Sony Xperia XZs (SO-03J, docomo)** into an iPod Classic-shaped device: local music player plus e-book reader, one package, one home screen, one click wheel.

**当前版本 1.42 / versionCode 46 · 8.48 MB** · 包名 `io.github.xejohv.libreraclassic`
安装包见 [Releases](https://github.com/xejohv/LibreraClassic/releases)，逐版本变化见 [docs/CHANGELOG.md](./docs/CHANGELOG.md)，部署手册见 [docs/DEPLOY.md](./docs/DEPLOY.md)。

---

## 源流与版权 / Lineage & Copyright

**本项目是 fork 合并体，不是原创项目，两半代码都来自别人：**

| 组成 | 上游 | 作者 / 版权 | 许可 |
| --- | --- | --- | --- |
| 启动器、音乐、相册、视频、游戏、设置（`app/`） | PodClassic → PodClassicMod → PodClassic-XZs | Copyright (c) 2021 **0x1317bf7**（PodClassic）；**Dora-z**（PodClassicMod）；**xejohv**（本分支改动） | MIT |
| 阅读器（`reader/`，包名 `com.foobnix.*`） | **Librera Reader 10.1.14**（[foobnix/LibreraReader](https://github.com/foobnix/LibreraReader)） | Copyright (C) **Ivan Ivanenko**（GitHub @foobnix）及上游贡献者 | GPL-3.0-or-later |

要点：

- **阅读器不是本项目写的**：`reader/` 整套来自 Librera Reader（安卓上支持格式最多的开源阅读器之一），本项目只在它上面做 iPod 化的界面、纸色、菜单与进程隔离，未删除、未修改任何上游版权与许可声明。
- **合并后的 APK 整体按 GPL-3.0-or-later 分发**：因为 `reader/` 是 GPLv3 代码，合并产物属于 GPLv3 衍生作品，APK 与阅读器部分的源码必须同许可提供；`app/` 里 PodClassic 派生的代码保留 MIT。再分发 APK 或修改版时须保留全部版权声明、公开对应源码、衍生作品沿用 GPLv3。
- 全部源码在本仓库公开，即 GPLv3 要求的「对应源码」；GPLv3 全文见 [LICENSES/GPL-3.0.txt](./LICENSES/GPL-3.0.txt)，MIT 全文见 [LICENSE](./LICENSE)，第三方组件归属见 [NOTICE.md](./NOTICE.md)。
- 第三方原生库与素材（PDFium、libarchive、libtiff、DjVuLibre、libmobi、LAME、字体等）各归其上游许可，随源码仓库一并提供。
- 「iPod」「iPod Classic」是 Apple Inc. 的商标，「Sony」「Xperia」是 Sony 的商标。本项目与这些公司无任何关联，未获其授权或背书，仅为自己一台旧机器的界面致敬，对外名称与蓝牙名沿用 iPod Classic 也仅限个人设备用途。
- 上游 PodClassic 的原始仓库与账号已下线（IzzyOnDroid 标记 `NoSourceSince`），本仓库是这条代码线的现存延续。

---

# 中文

## 外观与系统

- 金色机身配色 + 黑转盘 + 白色三角，满幅贴边；四套机身配色（暖银 / 暗黑 / 冰蓝 / 金桔）。
- 顶部单行标题栏：时间、播放状态、电量；无障碍服务掉线时在标题右侧画一枚红色「!」，**点它直接跳到系统无障碍页**，服务正常时该位置点击无反应。
- 状态栏、导航栏在任何界面都不显示（含相机、视频、蓝牙、游戏），下拉通知栏被顶部拦截条挡住。
- 本应用就是桌面（`CATEGORY_HOME`），冷启动只起主进程，约 1 秒；锁定竖屏。

## 音乐

- 转盘 = 上一曲 / 下一曲 / 快进快退，中央键 = 播放暂停，MENU = 菜单。
- **音量画在进度条本身**：转动盘或按机身音量键时，同一根条改画音量格数（两端时间位换成喇叭图标），条身位置不动，所以切换不跳动；停手 2 秒自动回进度显示，整页只有这一根条。
- 长按中央键 = 收藏 / 取消收藏（收藏项名前顶 ★）；随机 / 单曲 / 顺序 / 全部循环只从右上角图标切换。
- 封面、歌名、艺人、专辑、时间齐全；开关打开时按 LRC 显示歌词；睡眠定时、均衡器、AGC、胆机音效、DC 相位线性器都在设置里。
- CoverFlow 可左右转动浏览专辑；列表分歌曲 / 专辑 / 表演者 / 收藏 / 当前播放列表。

## 阅读（内建 Librera Reader）

- **和音乐在同一个 APK 里**，没有第二个 App：附加程序 → 阅读。有书页在后台时点「阅读」直接回到原书原页，没有才开书库。
- **阅读器跑在独立进程 `:reader`**，桌面与音乐进程里没有任何阅读器类，所以桌面不会被阅读拖慢。
- **书库是木纹书架**：暖白标题、奶油分段控件、羊皮纸书卡；打开即落在「最近」（按最后打开时间倒序，卡片带阅读进度），另有「全部」与「回收站」。
- **导入**：电子书丢进内部存储的 `Download` 根目录，书库顶栏显示「导入 N」，确认后搬进 `Download/Books`。Android 8 的 MTP 只把系统目录下的二级目录画成文件夹，自建根目录会被显示成一张空白文件，所以别往 `/sdcard/Books` 放。
- **五套纸色**：白纸 `#FFFFFF`、米黄 `#F5EDDA`、护眼绿 `#DCEAD8`、夜间 `#000000`、深灰 `#34343B`，按 Kindle / Apple Books / 微信读书 的惯例分档，相邻两套一眼能分。
- **PDF、DjVu、漫画按原色显示**：页面本身白纸黑字，只让页边跟着纸色走——版式书的「白」就是纸，染色只会让公文、印章、扫描件更难读。
- **翻页与缩放**：单指只在左右两条窄边翻页，页面中间单指拖动不再移动页面；双指缩放 + 平移，抬手自动吸附（低于整页适配回弹，100% 附近归位）；菜单亮度行右侧显示「缩放 128%」，点一下直接回 100%。
- **阅读菜单**（点页面中间或按 MENU）：字号、字体、背景（五色）、亮度、目录、书签、跳转。
- 格式：PDF、EPUB、MOBI / AZW / AZW3 / PDB / PRC（后台转 EPUB 缓存）、TXT / LOG / INI、Markdown、HTML / XML、DOC / DOCX / RTF / ODT / CHM / LIT（转 EPUB 后打开）、CBZ / CBR / ZIP、DjVu、TIFF。mobi/azw3 在进书库时即后台预转换，点开不再等。
- 删除进回收站保留 7 天（`/sdcard/.PodClassicTrash/`），可恢复或彻底删除。

## 快门键

- **半按 = 播放 / 暂停**，音乐界面、书页、书库三处一致。
- **全按 = 界面切换**：音乐界面 → 回到上次的书页（页码原样）；书页 / 书库 → 回音乐，书页留在后台任务里不清空；只有从书库退出才结束阅读后台。
- 半按与全按严格区分：全按在固件上先发 `FOCUS(DOWN)` 再发 `CAMERA`，所以半按改为 `FOCUS` 抬起后确认、留 180ms 观察窗，见到 `CAMERA` 就按全按处理，不会顺带切歌。

## 附加程序

- 侧栏一级菜单是：音乐 / 照片 / 视频 / 文件 / 附加程序 / 设置。
- 附加程序里是：游戏、录音、阅读、相机（快门键拍照）、蓝牙页（内嵌，不跳系统设置）、主题（机身配色）。
- 录音：开始 / 停止、录制实时走时、倒序列表、直接播放、长按二次确认删除；三档质量（192k / 128k / 64k），文件存 `Recordings/`。
- 视频：进度条可拖动快进（拖动时暂停并浮出目标时间），半屏靠 MENU 退出，全屏靠右下角按钮收回。
- 6 个游戏：俄罗斯方块、贪吃蛇、飞机大战、打砖块、数独、扫雷。全屏时下滑折叠转盘区，未全屏自动暂停，退出只保留侧面滑出。

## 设置

首页 5 个分组，点进去才是各项：

| 分组 | 内容 |
| --- | --- |
| 播放与显示 | 自动播放、播放顺序、随机、重复、睡眠定时、歌词、歌手与专辑、时间、CoverFlow |
| 音效 | 音效说明、均衡器、系统 AGC、TomSteady、胆机音效、DC 相位线性器 |
| 录音 | 录音质量 |
| 外观与操作 | 夜间模式、机身配色、屏幕亮度（自动 / 20 / 40 / 60 / 80 / 100%）、按键反馈 |
| 系统 | 关于本机、语言、辅助功能、资源监视器、结束阅读进程、恢复所有设置 |

- **关于本机**只读：音乐 / 视频 / 照片 / 容量 / 可用容量 / 版本 / S/N；车型明细进「容量」二级页；版本行连点 6 次进系统设置。
- **辅助功能**行显示实时状态，从系统设置开关回来立刻刷新，不用退桌面。
- **资源监视器**固定八行（可用内存 / 内存总量 / 内存占用 / 本应用内存 / 本应用 CPU / 应用进程 / 电池 / 电池温度），进页面自动读一次，按控制盘中心键重读。
- **结束阅读进程**：右侧实时显示 `:reader` 在跑还是在歇，点一下当场结束。
- **恢复所有设置**只留「恢复」一行：重置转盘偏好 → 纸色回夜间 → 清曲库索引缓存 → 偏好落盘 → 语言按默认重套 → 重启进程。
- 语言：自动 / 简体中文 / 繁体中文 / English，切换即时生效。

## 无障碍增强（需开启本应用的无障碍服务）

- 底部上滑 = 回桌面，底部上滑长按 = 最近任务；左、右侧滑 = 返回。
- 长按音量下键 1.2 秒 = 回桌面（从相机等外部应用逃生）。
- 顶部拦截下拉状态栏；系统电源菜单被替换成 PodClassic 风格；锁屏是自绘滑动解锁。
- 插线自动选「传输文件」，MTP 授权弹窗自动点「允许」（只在文案同时匹配时动手）。
- 进入系统设置、相机等没有导航栏的界面时，顶部覆盖一条悬浮返回条（`‹ 返回` / `iPod`）。
- 每次覆盖安装后无障碍绑定会被系统清空，标题栏出现红色「!」，此时点它即可跳到无障碍页重新开启。

## 数据目录

| 路径 | 内容 |
| --- | --- |
| `Download/Books` | 书库（放这里的电子书即出现在书库） |
| `Music/` | 音乐，插线直接拷 |
| `Recordings/` | 录音 |
| `.PodClassicTrash/` | 删书回收站，保留 7 天 |

## 部署

```powershell
$env:JAVA_HOME        = "<工作目录>\build-env\jdk-21.0.13+9"
$env:ANDROID_HOME     = "<工作目录>\build-env\sdk"
$env:GRADLE_USER_HOME = "<工作目录>\build-env\gradle-home"

cmd /c ".\gradlew.bat :app:assembleRelease --no-daemon"
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services io.github.xejohv.libreraclassic/io.github.xejohv.podclassicxzs.gestures.GestureService
adb shell settings put secure accessibility_enabled 1
```

- 签名读 `local.properties` + 仓库根 `podclassic.keystore`（两者都不入库）。缺失时退回 debug 签名：能装，但与旧正式签名不兼容，覆盖安装要卸载重装（会清数据）。
- 备份 keystore 与 local.properties，这是长期原地升级的唯一凭证。
- 完整重建清单、验收表与排障见 [docs/DEPLOY.md](./docs/DEPLOY.md)，交给 AI 的部署提示词见 [docs/AI-DEPLOY.md](./docs/AI-DEPLOY.md)。

## 适用机型

| 项 | 值 |
| --- | --- |
| 机型 | Sony SO-03J（Xperia XZs，docomo 日版） |
| 系统 | Android 8.0.0（API 26），arm64-v8a |
| 分辨率 | 1080×1920，density 480（densityDpi 540） |
| minSdk / targetSdk | 26 / 34 |
| applicationId | `io.github.xejohv.libreraclassic` |
| 版本 | 1.42（versionCode 46） |

## 已知限制

- 只在 SO-03J 上验证；界面 overscan、触摸区、字号按该分辨率校准，其它机型未测。
- 仅竖屏。
- Android 8 的 MTP 无法把自建根目录显示成文件夹，书必须经 `Download` 导入。
- 阅读器不含批注、绘图、词典、OPDS 书库与网页词典（原版入口已移除，格式支持保留）。
- 无障碍服务关闭时，手势、电源菜单、插线自动应答、悬浮返回条全部失效。
- 本包按单机使用场景调整，不作为通用播放器或通用阅读器发布；图书阅读不单独发布。

---

# English

## What it is

A single-APK retrofit for a **Sony Xperia XZs (SO-03J)**: iTunes-era iPod Classic look and click-wheel navigation, wrapping a local music player and a full e-book reader. One package (`io.github.xejohv.libreraclassic`), no second app, no launcher icon clutter.

## Look and system

- Gold body palette with black click wheel; four body themes (Warm Silver / Black / Ice Blue / Citrus).
- One-line title bar: time, playback state, battery. When the accessibility service drops, a red **!** is drawn next to the title — **tapping it opens the system accessibility settings**; while the service is connected the same spot does nothing.
- Status bar and navigation bar are hidden everywhere (camera, video, Bluetooth, games included); notification shade pulls are blocked by a top strip.
- The app is the home screen itself (`CATEGORY_HOME`); cold start brings up only the main process (~1 s). Portrait locked.

## Music

- Click wheel: previous / next / seek; center = play/pause; MENU = menu.
- **Volume is drawn on the progress bar itself**: the same bar switches to volume ticks (time labels become speaker icons) without moving or resizing, so nothing jumps; it falls back to elapsed time 2 s after the last input. There is no second volume bar anywhere.
- Long-press center = favourite / unfavourite (starred entries get a ★). Shuffle / single / sequential / repeat toggle from the top-right icon only.
- Cover art, title, artist, album, times; LRC lyrics when enabled; sleep timer; equalizer, system AGC, TomSteady, tube-amp and DC phase linearizer in Settings.
- CoverFlow browsing; lists for songs / albums / artists / favourites / current playlist.

## Reading (built-in Librera Reader)

- **Same APK as the player** — no second install. Extra Apps → Reading. If a book page is still in the background, tapping Reading returns to that exact page; otherwise the library opens.
- The reader runs in its own process `:reader`; the launcher/music process carries no reader classes, so the home screen stays fast.
- **Wood-shelf library**: warm-white title, cream segmented controls, parchment book cards. Opens on **Recent** (last-opened first, with progress); All and Trash are one tap away.
- **Import**: drop e-books into the internal `Download` folder; the library header shows "Import N" and moves them into `Download/Books`. On Android 8, MTP renders self-made root folders as blank files, so `/sdcard/Books` never shows up on a PC — hence the import step.
- **Five paper colours**: white `#FFFFFF`, sepia `#F5EDDA`, eye-care green `#DCEAD8`, night `#000000`, graphite `#34343B` — ordered after Kindle / Apple Books / WeChat Read conventions, neighbouring pairs clearly distinguishable.
- **PDF, DjVu and comics stay in original colour**: the page keeps its own paper; only the margin follows the paper colour, because re-mapping a page wrecks forms, stamps and scans.
- **Paging and zoom**: single-finger paging lives only in two narrow edge strips; dragging in the middle no longer moves the page. Two fingers zoom and pan, with snap-back on release (below fit-page bounces back, near 100% settles to exactly 100%). The brightness row shows "Zoom 128%"; tap it to return to 100%.
- **Reader menu** (tap the middle or press MENU): size, font, background (five papers), brightness, contents, bookmarks, jump.
- Formats: PDF, EPUB, MOBI / AZW / AZW3 / PDB / PRC (pre-converted to EPUB in the background), TXT / LOG / INI, Markdown, HTML / XML, DOC / DOCX / RTF / ODT / CHM / LIT (converted to EPUB), CBZ / CBR / ZIP, DjVu, TIFF.
- Deleting a book sends it to a 7-day trash (`/sdcard/.PodClassicTrash/`), restorable or permanently removed.

## Shutter button

- **Half press = play / pause**, identical on the music screen, the book page and the library.
- **Full press = switch screens**: music → last book page, same page number; book page / library → music, with the book page kept alive in the background task. Only leaving from the library clears the reader.
- Half and full press are separated properly: the firmware sends `FOCUS(DOWN)` before `CAMERA`, so play/pause now commits on `FOCUS` release with a 180 ms window; seeing `CAMERA` means full press and no track change.

## Extra Apps

- Side menu: music / photos / videos / files / extra apps / settings.
- Extra Apps itself: games, voice memos, reading, camera (shutter key takes the photo), Bluetooth page (in-house, no system settings), themes (body colours).
- Voice memos: start / stop, live duration, newest-first list, playback, long-press delete with confirmation; three quality tiers (192k / 128k / 64k), files in `Recordings/`.
- Video: draggable seek bar (pauses and shows the target time while dragging), half-screen exits via MENU, fullscreen retracts from the corner button.
- Six games: Tetris, Snake, Shooter, Breakout, Sudoku, Minesweeper. Fullscreen folds the wheel away; pausing when not fullscreen; exiting only by side swipe.

## Settings

Five groups:

| Group | Items |
| --- | --- |
| Playback & display | autoplay, play order, shuffle, repeat, sleep timer, lyrics, artist & album, time, CoverFlow |
| Sound | sound help, equalizer, system AGC, TomSteady, tube amp, DC phase linearizer |
| Recording | recording quality |
| Look & controls | night mode, body colour, screen brightness (auto / 20 / 40 / 60 / 80 / 100%), key feedback |
| System | About, language, accessibility, resource monitor, end reader process, restore all settings |

- **About** is read-only: songs / videos / photos / capacity / free / version / serial; storage details in a sub-page; tapping the version row 6 times opens system settings.
- **Accessibility** row reflects live state — toggling it in system settings updates the row immediately.
- **Resource monitor** is a fixed eight rows (free RAM / total RAM / used RAM / this app's RAM / this app's CPU / app processes / battery / battery temperature), read once on entry, refreshed by pressing the wheel centre.
- **End reader process** shows whether `:reader` is running and kills it on tap.
- **Restore all settings**: one "Restore" row only — wheel prefs, paper colour back to night, library index cache cleared, prefs flushed, language reset, process restarted.
- Language: auto / Simplified Chinese / Traditional Chinese / English, applied in place.

## Accessibility add-ons (service required)

- Swipe up from the bottom = home; long swipe-up = recents; swipe from either edge = back.
- Hold volume-down 1.2 s = home (escape hatch from camera or other apps).
- Top strip blocks the notification shade; the system power menu is replaced by the PodClassic one; lock screen is a custom slide-to-unlock.
- Plugging in USB auto-selects "Transfer files" and auto-accepts the MTP prompt (only when the exact labels match).
- System settings and camera have no navigation bar, so a floating `‹ Back` / `iPod` bar is overlaid there.
- Every reinstall clears the accessibility binding, which brings back the red **!** — tap it to reopen the accessibility page.

## Data folders

| Path | Contents |
| --- | --- |
| `Download/Books` | Library; drop e-books here |
| `Music/` | Music, copy over USB |
| `Recordings/` | Voice memos |
| `.PodClassicTrash/` | Deleted books, 7 days |

## Build & deploy

```powershell
$env:JAVA_HOME        = "<workdir>\build-env\jdk-21.0.13+9"
$env:ANDROID_HOME     = "<workdir>\build-env\sdk"
$env:GRADLE_USER_HOME = "<workdir>\build-env\gradle-home"

cmd /c ".\gradlew.bat :app:assembleRelease --no-daemon"
adb install -r app\build\outputs\apk\release\app-release.apk
adb shell settings put secure accessibility_enabled 0
adb shell settings put secure enabled_accessibility_services io.github.xejohv.libreraclassic/io.github.xejohv.podclassicxzs.gestures.GestureService
adb shell settings put secure accessibility_enabled 1
```

- Signing reads `local.properties` plus `podclassic.keystore` at the repo root; neither is committed. Without them the build falls back to a debug key — installable, but incompatible with older release builds (uninstall wipes data).
- Back up the keystore and `local.properties`; they are the only way to upgrade in place.
- Full rebuild checklist, acceptance table and troubleshooting: [docs/DEPLOY.md](./docs/DEPLOY.md). AI prompt: [docs/AI-DEPLOY.md](./docs/AI-DEPLOY.md).

## Device

| Item | Value |
| --- | --- |
| Device | Sony SO-03J (Xperia XZs, docomo) |
| OS | Android 8.0.0 (API 26), arm64-v8a |
| Screen | 1080×1920, density 480 (densityDpi 540) |
| minSdk / targetSdk | 26 / 34 |
| applicationId | `io.github.xejohv.libreraclassic` |
| Version | 1.42 (versionCode 46) |

## Known limits

- Verified only on SO-03J; overscan, touch zones and font sizes are calibrated for that screen.
- Portrait only.
- Android 8 MTP cannot show self-made root folders as folders; books must go through `Download` + Import.
- The reader ships without annotation, drawing, dictionary, OPDS catalogue and web-dictionary entries (entry points removed, format support kept).
- With the accessibility service off, gestures, power menu, USB auto-answer and the floating back bar all stop working.
- Tuned for one device used standalone; not published as a general-purpose player or reader. The reading feature is not released as a separate app.

## License summary

`app/` (PodClassic lineage) is MIT. `reader/` (Librera Reader, Copyright (C) Ivan Ivanenko / foobnix) is GPL-3.0-or-later, therefore the combined APK is distributed under GPL-3.0-or-later. Full texts: [LICENSE](./LICENSE) and [LICENSES/GPL-3.0.txt](./LICENSES/GPL-3.0.txt); third-party attribution in [NOTICE.md](./NOTICE.md). Not affiliated with Apple or Sony; "iPod" and "Xperia" are their owners' trademarks.
