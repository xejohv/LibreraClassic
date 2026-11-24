# NOTICE — 归属、来源与许可 / Attribution, Provenance and Licensing

本文件是 [README.md](./README.md) 中「源流与版权」的完整版，供再分发或二次修改时对照。
This file is the long form of the "Lineage & Copyright" section in [README.md](./README.md).

## 1. 两个上游，两套许可 / Two upstreams, two licenses

| 目录 | 上游项目 / Upstream | 权利人 / Copyright holder | 许可 / License |
| --- | --- | --- | --- |
| `app/`（启动器、音乐、相册、视频、游戏、设置） | PodClassic 1.5（MIT）→ PodClassicMod → PodClassic-XZs | Copyright (c) 2021 **0x1317bf7**（PodClassic）；**Dora-z**（PodClassicMod）；**xejohv**（本分支） | MIT（全文：[LICENSE](./LICENSE)） |
| `reader/`（阅读器，Java 包名 `com.foobnix.*`、`org.ebookdroid.*`） | **Librera Reader 10.1.14**，https://github.com/foobnix/LibreraReader | Copyright (C) **Ivan Ivanenko**（GitHub @foobnix）及上游贡献者 | GPL-3.0-or-later（全文：[LICENSES/GPL-3.0.txt](./LICENSES/GPL-3.0.txt)） |

两块代码在同一个 APK 里运行（启动器主进程 + 阅读器 `:reader` 进程），属于**合并作品**，不是两个独立分发物。

## 2. 合并作品的许可结论 / License of the combined work

- `reader/` 以 GPL-3.0-or-later 授权，具有传染性。因此**发布的 APK 整体、以及包含 `reader/` 的源码集合，均按 GPL-3.0-or-later 分发**；其中 `app/` 目录里 PodClassic 派生的代码同时保留 MIT 授权（MIT 与 GPLv3 兼容，可继续按 MIT 使用该部分）。
- 再分发 APK 或修改版时必须遵守 GPLv3，至少包括：
  1. 保留全部版权声明与许可声明，附上 GPLv3 全文；
  2. 提供或指明「对应源码」（本仓库即为对应源码，含全部修改）；
  3. 修改后的作品继续以 GPLv3 授权，不得追加额外限制；
  4. 不提供任何担保（GPLv3 §15–§17 的免责条款）。
- 本项目未删除、未改名、未改写任何上游版权头或许可文件；`reader/` 内文件的包名与类名保持上游原样，以便对照上游源码。

## 3. 本分支的修改范围 / What this fork changed

只为单机 iPod 化改造，不改上游的许可与署名：

- `app/`：iPod Classic 外观与转盘交互、快门键、机身配色、字体与标题栏、无障碍手势层、资源监视器、导入与回收站入口、快照式设置页、语言切换。
- `reader/`：新增 `io.github.xejohv.classic.*`（书库、书页菜单、纸色、封面）与进程/任务隔离、格式库加载容错、PDF 原色、双指缩放与单指禁行、移除批注 / 绘图 / 词典 / OPDS 入口。
- 构建：单 APK 双模块、`resourceConfigurations` 限中英、R8 与资源压缩。

## 4. 打包的第三方组件 / Bundled third-party components

| 组件 | 用途 | 许可（随上游） |
| --- | --- | --- |
| PDFium（`libpdfium.so`, `libpdfiumandroid.so`） | PDF 渲染 | BSD-3-Clause（Google / Chromium） |
| DjVuLibre（`libdjvu.so`） | DjVu 解码 | GPL-2.0-or-later |
| libmobi（`libmobi.so`） | MOBI / AZW 解析 | LGPL（见上游 libmobi） |
| libtiff（`libtiff.so`） | TIFF 解码 | libtiff License |
| libarchive（`libarchive.so`） | CBZ / CBR / ZIP 解包 | BSD-2-Clause |
| LAME（`liblame.so`） | 录音 MP3 编码 | LGPL-2.0 |
| hqx（`libhqx.so`） | 像素放大 | 见上游 hqx |
| 字体（`reader/src/main/assets/fonts`） | 阅读字体 | 各字体上游许可（多为 OFL / Apache-2.0） |

若需精确到版本的许可文本，以上游仓库与各组件发行包内的 LICENSE 为准；本仓库只做汇总，不替代原始许可。

## 5. 商标 / Trademarks

- 「iPod」「iPod Classic」为 Apple Inc. 商标；「Sony」「Xperia」为 Sony Group Corporation 商标。
- 本项目与上述公司**无关联、未获授权或背书**，仅为个人一台旧设备的界面致敬；应用内名称、蓝牙名「iPod Classic」仅用于该个人设备。
- 上游名称「Librera」「Librera Reader」为上游项目名称，本项目对阅读器部分的引用仅用于说明来源。

## 6. 免责 / No warranty

软件按「现状」提供，不附带任何明示或暗示担保。刷写、覆盖安装、无障碍权限、MTP 自动应答等操作可能导致数据丢失或系统行为异常，风险由使用者自行承担。
