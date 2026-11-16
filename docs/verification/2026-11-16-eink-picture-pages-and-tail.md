# 0.6145 正文图片与末页验证

## 问题与实现

用户反馈正文图片跨两页，末页带回大量已读内容并使页头位置混乱。
正文到达文档底部时，浏览器会将滚动位置限制到最后一整屏的起点；
图片分段也把末段起点限制到能够显示整段的位置，造成大幅重复。

- `reading-layout.js` 按实际可视高度限制正文图片，保留彩色与比例。当前页放不下的图片整体移到下一页；超长图显示完整缩略图。单图的图片块和能够放下一屏的说明一起落页。没有 RichText 等正文类名的想法或网页也按实际内容图片识别。
- `reading-pager.js` 保存本次正反向翻页起点，沿用按钮的 `scrollBy` 路径与文字重叠。文末加入不可交互的留白，末页保持正常起点。起点避开图片中间，防止图片被截断。
- `web_view_helper.lua` 在页面完成加载后启动阅读排版；`reading_pager.lua` 注入同一排版与翻页脚本。实体键继续触发原有虚拟按钮回调。
- 图片加载与尺寸变化合并排版；初次排版保留页头，主动回到顶部时不被排版队列带回其他位置。
- `ImageTiles` 末段从正常步长起点截取剩余区域。`EinkImageActivity` 等比、顶部对齐显示该区域，末段保留空白。
- `image-viewer.js` 支持点击窄长图所在图片块的空白；图片说明中的链接和按钮继续执行各自操作。

评论与收藏继续跟随正文，点赞、感谢和固定底部操作栏隐藏。动态图仍使用静态预览。

## 本次回归

| 检查 | 结果 |
| --- | --- |
| `tests/reading_pager_test.py` | Chromium 中 21 项通过；覆盖不同字号文字、连续图片与说明、单图段落、没有正文类名的图片、长图完整缩略图、末页留白、正反向起点、脚本重注入、图片加载锚点、顶部位置、实际可视视口、输入状态和图片点击 |
| `ImageTilesTest` | 104 组长图/宽图覆盖与区域采样上限通过；额外末段断言通过 |
| `ReadingKeyBridgeTest` | 生产 Java 分发器与真实 LuaJVM 双向桥接通过；Page 键触发同一虚拟按钮，消费 UP、repeat 和 MULTIPLE，回调失败时不产生原生二次翻页 |
| `PageNavigationTest`、`ReadingKeyTest` | 主页栏目、日报等列表、正文、返回栈、双栏、菜单重建、弹窗、开关、方向及销毁回归通过 |
| `PageStepTest` | 30 组原生列表字号与可读高度覆盖通过 |

关键复现数据：100 行、每行 30px、视口 640px、两行重叠时，起点为
`[0,580,1160,1740,2320,2900]`。末页第 99 行位于屏幕 y=70，上一页返回 2320。
1000×1605 原尺寸图片、1000×1400 视口、20px 重叠时，末段 y=1380、height=225。

无 RichText 类名的长图用例在补齐通用图片规则前失败：下一页图片底部 y=5641，超出 640px 视口。
补齐后与其余用例一起通过。

命令在仓库根目录执行：

```bash
env PAGER_TEST_CHROMIUM=/home/mofanke/.cache/ms-playwright/chromium_headless_shell-1243/chrome-headless-shell-linux64/chrome-headless-shell python3 tests/reading_pager_test.py
javac -cp /tmp/hydrogen-luajvm-core.jar:/tmp/hydrogen-android-sdk/platforms/android-37.0/android.jar -d /tmp/hydrogen-v5-java-tests app/src/main/java/com/hydrogen/ReadingKeyDispatcher.java tests/ReadingKeyBridgeTest.java tests/PageNavigationTest.java tests/ReadingKeyTest.java app/src/main/java/com/hydrogen/PageStep.java tests/PageStepTest.java app/src/main/java/com/hydrogen/ImageTiles.java tests/ImageTilesTest.java
```

Java 测试使用 JDK 21，与应用相同版本的 LuaJVM jar。
依次执行上述五个测试主类，classpath 为 `/tmp/hydrogen-v5-java-tests:/tmp/hydrogen-luajvm-core.jar`。
Java 菜单对象模拟 Android 菜单接口，浏览器使用合成正文；这些回归未在真实 Android Window 中注入事件。

## 构建与制品

最终版本为 `0.6145-eink`，versionCode 为 6145，Android API 24 及以上。

```bash
env JAVA_HOME=/tmp/hydrogen-build-tools/jdk-21.0.13+9 GRADLE_USER_HOME=/tmp/hydrogen-gradle-cache ANDROID_HOME=/tmp/hydrogen-android-sdk ANDROID_USER_HOME=/tmp/hydrogen-build-android-home bash gradlew :app:precompileLuaAssets :app:assembleRelease :app:cleanLuaAssets --no-daemon -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' --console=plain -I /tmp/hydrogen-repositories.init.gradle
```

- 最终 `BUILD SUCCESSFUL in 18s`；214 个 Lua 文件编译成功、0 失败，Release 打包通过。Java 与 Release Lint 在本次首次构建通过，最终增量构建复用结果。
- 临时移开 JDK 厂商限定文件，构建后恢复。源码 `.luac` 残留为 0，`git diff --check` 通过。
- `apksigner verify --verbose` 验证通过，v2、v3 签名有效；`zipalign -c 4` 通过。
- 从已发布 0.6144 下载 APK 对比签名证书，证书 SHA-256 相同，支持覆盖安装。
- APK 内四个阅读 JS 与本次源码逐字节一致；版本、翻页 helper 和通用 WebView helper 的 Lua 字节码存在，APK metadata 为 6145 / 0.6145-eink。
- `Hydrogen_eink_0.6145.apk`，8,342,314 字节，SHA-256 为 `3d643ee406126e28b42426b74772e20557548067730a15c9f8e9749df886bea0`。

制品发布页：[eink-v0.6145](https://github.com/mofanke/Hydrogen/releases/tag/eink-v0.6145)。

## 实机限制

B7 Pro 上的正文分页、图片清晰度、加载速度、内存占用、残影与实际按键事件仍未实机验证。
浏览器、JVM 和 APK 构建结果只证明各自检查覆盖的行为。
