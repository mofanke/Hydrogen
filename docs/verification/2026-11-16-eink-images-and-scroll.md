# 0.6142 墨水屏图片与正文翻页验证

## 原因与范围

用户反馈 0.6141 实体键翻页跳过较多内容，并确认原有屏幕翻页按钮的效果较准确。
本次复用原按钮的 `window.scrollBy` 路径与标题栏时序，按键与按钮走同一个入口。
取消翻页前主动折叠标题栏的操作；可视视口小于布局视口时限制步长，保留默认两行文字重叠。
同一次翻页尚未完成时不重复提交。

正文去掉固定底部操作栏，点赞和感谢不显示，评论与收藏跟随正文出现在回答末尾。
正文图片保持彩色与比例；图片加载和排版更新按帧合并，用文字锚点尽量保持阅读位置。

图片点击后进入原生分段查看器：适应宽度模式看长图，原尺寸模式按行、列看宽图。
静态图片使用 `BitmapRegionDecoder.decodeRegion`，解码当前段并使用 8 MiB 缓存。
单个解码区域的采样上限为 400 万像素；不支持区域解码的格式使用约 200 万像素上限的静态首帧预览。
查看器支持实体键、底部箭头、相邻段重叠和系统文件保存。

## 实际检查

| 检查 | 结果 |
| --- | --- |
| `python3 tests/reading_pager_test.py` | 11 项通过；包含真实 Chromium 2 倍页面缩放、布局视口与可视视口差异、16–48 字号连续文字覆盖、图片加载后的阅读位置、图片比例及文末评论桥接 |
| `javac -d /tmp/hydrogen-v2-java-tests app/src/main/java/com/hydrogen/ImageTiles.java tests/ImageTilesTest.java` 与 `java -cp /tmp/hydrogen-v2-java-tests ImageTilesTest` | 104 组长图、原尺寸宽图、大小图和随机尺寸覆盖测试通过，检查行列无缺口、首尾像素覆盖及采样上限 |
| `javac -cp /tmp/hydrogen-luajvm-core.jar -d /tmp/hydrogen-v2-java-tests tests/ReadingKeyTest.java` 与 `java -cp /tmp/hydrogen-v2-java-tests:/tmp/hydrogen-luajvm-core.jar ReadingKeyTest` | 音量键、PageUp/PageDown、反转、开关、宿主路由、重复提交和销毁契约通过 |
| `bash gradlew :app:precompileLuaAssets :app:assembleRelease :app:cleanLuaAssets --no-daemon -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' --console=plain -I /tmp/hydrogen-repositories.init.gradle` | 最终 `BUILD SUCCESSFUL in 22s`；213 个 Lua 文件成功，0 失败；Java 编译与 Release Lint 门禁通过；`.luac` 产物清理后残留 0 |
| `apksigner verify --verbose` | APK 验证通过，v2、v3 签名通过 |
| `zipalign -c 4` | 通过 |
| APK 内容检查 | 包含 `eink-content.js` 与 `EinkImageActivity` DEX 类 |
| `git diff --check` | 通过 |

使用前次已准备的 Temurin JDK 21、Gradle 9.7.1、Android SDK 37.0 和仓库外的临时依赖镜像配置。
构建时临时移开 Oracle 厂商限定文件，完成后已恢复。
Chromium 通过 `PAGER_TEST_CHROMIUM` 指向宿主已有浏览器。

## 制品与限制

- 文件：`Hydrogen_eink_0.6142.apk`，8,332,299 字节。
- 版本：`0.6142-eink`，versionCode 为 `6142`。
- SHA-256：`e548757bd417020383ad2ab5e8072d2bd52cd67a97ea8ffe9ff4a4776f057b44`。
- 使用与 0.6141 相同的包名和上游公开签名。

上述检查验证分页计算、浏览器排版、代码构建与安装包签名。
未在 Android 设备上验证原生图片区域解码、安装或启动，未测量实际内存峰值。
B7 Pro 的清晰度、加载速度、厂商按键行为和残影尚未实机验证。
GIF 等不支持区域解码的格式使用静态首帧预览，原尺寸查看按钮不可用；保存仍保留原文件。
