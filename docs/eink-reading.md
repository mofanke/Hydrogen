# 墨水屏按键阅读

本分支基于 Hydrogen 的 `448762c`，版本为 `0.6143-eink`，支持 Android 7.0（API 24）及以上。
保留原有彩色图片与主题。

## 使用

在设置的“墨水屏翻页”中开启“实体键翻页（墨水屏）”。新安装时默认开启。

| 按键 | 行为 |
| --- | --- |
| 音量减、PageDown | 当前列表或正文下一屏 |
| 音量加、PageUp | 当前列表或正文上一屏 |

“反转音量翻页键”可以调换音量键方向。PageUp/PageDown 保持其标示方向。
启用实体键翻页时，其优先级高于“音量键切换回答”。关闭实体键翻页后可使用原有的回答切换功能。
到达正文首尾时停留在当前回答。长按一次只翻一屏，释放后才能再次翻页。
输入文字时按键由原控件处理。设置开关关闭时 PageUp/PageDown 交回系统与 WebView。

屏幕上的悬浮滚动按钮也使用相同的分页计算，无需开启实体键翻页。
正文翻页复用原有按钮的 `window.scrollBy` 路径与标题栏处理顺序。
步长优先使用实际可视视口高度，保留默认两行重叠，可调为 1–5 行；同一次翻页尚未完成时不重复提交。
点赞、感谢和固定底部操作栏不显示，评论与收藏入口跟随正文，仅在回答末尾出现。

## 主页、日报与其他阅读页面

页面基类统一接入实体键和工具栏的“上一屏”“下一屏”箭头。
主页的推荐、热榜、想法、关注以及侧栏中的日报、收藏、关注内容列表使用当前可见栏目。
日报正文、文章、网页和本地正文复用网页翻页；搜索结果、历史、问题回答列表、用户和话题内容等原生列表按可读高度翻页。

列表扣除窗口裁剪、内边距和底部导航遮挡，按可见文字行高保留重叠，取消正在进行的列表惯性滚动并即时翻页。
到列表底部时沿用当前模型的加载更多机制；空列表先加载首页；新数据追加后保持当前位置，下一次按键再前进。
返回上一页时恢复前页按键；双栏尊重当前焦点，并选择所在栏最上层的页面。
侧边抽屉展开时暂停底层列表翻页。

评论列表、日报栏目列表和通用底部弹窗支持实体键翻页，长按一次只翻一屏。
输入文字时保留控件原有按键行为。工具栏菜单重建后继续保留翻页箭头。

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

统一页面路由与原生步长测试：

```bash
javac -cp /path/to/luajvm-core-1.0.0.jar -d /tmp/hydrogen-navigation-tests tests/PageNavigationTest.java app/src/main/java/com/hydrogen/PageStep.java tests/PageStepTest.java
java -cp /tmp/hydrogen-navigation-tests:/path/to/luajvm-core-1.0.0.jar PageNavigationTest
java -cp /tmp/hydrogen-navigation-tests PageStepTest
```
