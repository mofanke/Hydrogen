# 墨水屏按键阅读

本分支基于 Hydrogen 的 `448762c`，版本为 `0.6142-eink`，支持 Android 7.0（API 24）及以上。
保留原有彩色图片与主题。

## 使用

在设置的“回答页”中开启“实体键翻页（墨水屏）”。新安装时默认开启。

| 按键 | 行为 |
| --- | --- |
| 音量减、PageDown | 当前回答下一屏 |
| 音量加、PageUp | 当前回答上一屏 |

“反转音量翻页键”可以调换音量键方向。PageUp/PageDown 保持其标示方向。
启用实体键翻页时，其优先级高于“音量键切换回答”。关闭实体键翻页后可使用原有的回答切换功能。
到达正文首尾时停留在当前回答。长按一次只翻一屏，释放后才能再次翻页。
输入文字时按键由原控件处理。设置开关关闭时 PageUp/PageDown 交回系统与 WebView。

屏幕上的悬浮滚动按钮也使用相同的分页计算，无需开启实体键翻页。
正文翻页复用原有按钮的 `window.scrollBy` 路径与标题栏处理顺序。
步长优先使用实际可视视口高度，保留默认两行重叠，可调为 1–5 行；同一次翻页尚未完成时不重复提交。
点赞、感谢和固定底部操作栏不显示，评论与收藏入口跟随正文，仅在回答末尾出现。

## 图片阅读

正文图片限制在页面宽度内，保持比例与彩色。
点击进入图片查看器，长图按可读区域分段，音量键、PageUp/PageDown 或底部箭头切换分段。
相邻段保留约 20dp 重叠。适应宽度模式按纵向阅读；“原尺寸”模式按行、列分段，无需拖动。
切换原尺寸或屏幕尺寸时尽量保持当前图片区域。

静态 JPEG、PNG、WebP 等受 Android 区域解码器支持的格式只解码当前区域，使用 8 MiB 分段缓存。
位图采样将单个解码区域限制在 400 万像素以内，保留 ARGB 彩色。
不支持区域解码的格式使用最多约 200 万像素的静态首帧预览，此时原尺寸按钮不可用。
这包括部分动态图格式；点击 GIF 预览直接打开查看器。

正文有尺寸信息的图片预留比例空间；图片加载与布局变化合并更新，通过文字锚点尽量保持阅读位置。
保存按钮使用系统文件保存界面，保存原文件。

## 构建与验证

使用 JDK 21、项目 Gradle Wrapper 和 Android SDK。构建命令：

```bash
bash gradlew :app:precompileLuaAssets
bash gradlew :app:cleanLuaAssets
bash gradlew :app:assembleRelease
```

分页回归使用 Python Playwright 与 Chromium：

```bash
python3 -m pip install playwright
python3 -m playwright install chromium
python3 tests/reading_pager_test.py
```

已有 Chromium 时可通过 `PAGER_TEST_CHROMIUM` 指定可执行文件。
测试覆盖原算法漏行复现、不同字号、悬浮遮挡、长图片、即时翻页、首尾边界、
可视视口与布局视口差异、重叠设置、输入焦点、图片加载后的锚点和文末操作入口。

按键与生命周期测试使用构建依赖中的 `luajvm-core-1.0.0.jar`：

```bash
java --class-path /path/to/luajvm-core-1.0.0.jar tests/ReadingKeyTest.java
```

设备专用翻页键须由系统映射为音量键或 PageUp/PageDown。
尚未在用户的实体墨水屏设备上验证刷新模式、残影与厂商专用键码。B7 Pro 上的清晰度、速度、内存占用和残影未实机验证。

图片分段的覆盖与采样上限测试：

```bash
javac -d /tmp/hydrogen-image-tests app/src/main/java/com/hydrogen/ImageTiles.java tests/ImageTilesTest.java
java -cp /tmp/hydrogen-image-tests ImageTilesTest
```
