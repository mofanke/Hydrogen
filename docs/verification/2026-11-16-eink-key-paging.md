# 墨水屏实体键翻页验证

## 变更与原因

基于上游 `448762c`，新增当前回答内的实体按键分页，版本为 `0.6141-eink`。
屏幕翻页按钮直接滚动 `window.innerHeight` 时，悬浮操作栏遮挡和页边界切行会使部分文字无法完整显示。
分页改为按实际可读高度计算，并保留默认两行的文字重叠。

影响范围：回答页的实体键与屏幕翻页按钮、三个阅读设置项、原生按键分发、原生视口遮挡换算、APK 版本信息。
文字输入时交回控件；长按只翻一屏；页首与页尾停留在当前回答。

## 实际验证

| 命令 | 结果 |
| --- | --- |
| `python3 tests/reading_pager_test.py` | Chromium 9 项通过；复现原算法漏行；修复后 16、20、32、48 字号均完整覆盖 100 行文字；长图片与悬浮按钮避让通过 |
| `java --class-path /tmp/hydrogen-luajvm-core.jar tests/ReadingKeyTest.java` | 使用 APK 同版本 LuaJVM 引擎，按键方向、反转、设置开关、宿主路由、切换回答与销毁后回调契约通过 |
| `bash gradlew :app:precompileLuaAssets :app:assembleRelease --no-daemon -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' --console=plain -I /tmp/hydrogen-repositories.init.gradle` | `BUILD SUCCESSFUL in 3m 19s`；213 个 Lua 文件预编译成功，0 失败；Java 编译与 Release Lint 门禁通过 |
| `bash gradlew :app:cleanLuaAssets` | 通过，源码目录残留 `.luac` 数为 0 |
| `apksigner verify --verbose app/build/outputs/apk/release/Hydrogen_eink_0.6141.apk` | `Verifies`，v2 与 v3 签名通过 |
| `zipalign -c -v 4 app/build/outputs/apk/release/Hydrogen_eink_0.6141.apk` | `Verification successful` |
| APK ZIP 内容检查 | 213 个 `.luac`，0 个 `.lua`；包含 `assets/helpers/reading_pager.luac` 与 `assets/static/js/features/reading-pager.js` |
| `git diff --check` | 通过 |

Chromium 测试通过 `PAGER_TEST_CHROMIUM` 指向宿主已有的 headless Chromium。

本地使用 Temurin JDK 21.0.13、Gradle 9.7.1、Android SDK 37.0。
构建时临时移开上游 Oracle JDK 厂商限定文件，使用已安装的 JDK；构建后恢复该文件。
Maven Central 返回 HTTP 429，使用仓库外的临时 Gradle init script 指定镜像完成依赖下载。
临时构建配置与环境变量不写入仓库。

构建中 SDK 缓存目录出现只读警告；LuaJVM 依赖出现 D8 stack map 警告。最终构建成功，运行时兼容性仍需实机验证。

## 制品

- 文件：`Hydrogen_eink_0.6141.apk`
- 大小：8,321,400 字节
- 包名：`com.zhihu.hydrogen.x`
- versionCode：`6141`
- versionName：`0.6141-eink`
- SHA-256：`38a4cef7bbcedd35265a34f9d8ebe29c22c68672096f24fa45c70fa2860740b6`
- 使用上游公开构建签名。

## 未完成的实机验证

当前宿主不支持 KVM 硬件加速，软件模拟器也未成功启动；未验证 APK 在 Android 设备上的安装与启动。
尚未收到用户设备型号与专用键码。已实现音量键及 PageUp/PageDown，厂商自定义键码、墨水屏刷新模式与残影待实机验证。
未进行知乎账号登录或在线回答浏览测试。
