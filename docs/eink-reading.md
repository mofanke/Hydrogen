# 墨水屏按键阅读

本分支基于 Hydrogen 的 `448762c`，版本为 `0.6141-eink`，支持 Android 7.0（API 24）及以上。
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
每屏按 WebView 的实际可读区域计算，避让作者卡片、底部操作栏、悬浮翻页按钮与窗口裁剪区域。
上下页默认保留两行重叠文字，可调为 1–5 行。正文末尾补齐底部避让空间。
翻页即时落位，并收起可折叠标题区域。

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
原生像素与 CSS 像素转换、重叠设置、输入焦点、短正文与尚未布局的视图。

按键与生命周期测试使用构建依赖中的 `luajvm-core-1.0.0.jar`：

```bash
java --class-path /path/to/luajvm-core-1.0.0.jar tests/ReadingKeyTest.java
```

设备专用翻页键须由系统映射为音量键或 PageUp/PageDown。
尚未在用户的实体墨水屏设备上验证刷新模式、残影与厂商专用键码。
