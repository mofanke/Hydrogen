# 0.6144 Page 按键与虚拟按钮一致性验证

## 问题与修复

用户反馈虚拟按钮准确，PageUp/PageDown 表现不同。
代码存在不同的事件路径：虚拟按钮直接调用翻页；实体键可因输入判断或失败返回回退到 Android 控件默认翻页。
此前检查覆盖分页和 Lua 路由，没有联合验证原生事件消费与 Java/Lua 往返。

本次主页面和独立阅读 Activity 的实体键直接调用当前虚拟翻页菜单。
图片查看器的实体键直接调用底部按钮。分页步长与图像分段算法未调整。
共享 ReadingKeyDispatcher 消费按下、重复、松开和 MULTIPLE 事件，避免控件再执行一轮默认翻页。
Page 键在页面已有翻页控件时，即使回调失败也不回退到第二次原生翻页。
当前焦点的 onCheckIsTextEditor 用于输入判断，移除 isAcceptingText 独立判定。
列表滚动后的加载更多异常被独立捕获，已完成的滚动仍返回消费成功。

这些是源码中确认并修复的差异路径，尚未取得 B7 Pro 的实际事件记录，不能证明其具体触发条件。

## 实际检查

| 检查 | 结果 |
| --- | --- |
| ReadingKeyBridgeTest | 真实生产 ReadingKeyDispatcher 与 LuaJVM JavaCall 联合测试；Java→Lua→Java 菜单→Lua 按钮回调；比较虚拟按钮和 PageDown 距离、PageUp 方向、UP、repeat、独立/序列 MULTIPLE、输入状态、失败返回后的原生二次翻页隔离；通过 |
| PageNavigationTest | 当前页面菜单实际被按键调用；加载更多失败仍消费按键；主页栏目、日报列表、返回栈、双栏、菜单重建和弹窗回归通过 |
| ReadingKeyTest | 回答页方向、开关、反转、重复和销毁回归通过 |
| `bash gradlew :app:precompileLuaAssets :app:assembleRelease :app:cleanLuaAssets --no-daemon -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' --console=plain -I /tmp/hydrogen-repositories.init.gradle` | 最终 BUILD SUCCESSFUL in 22s；214 个 Lua 文件成功，0 失败；Java、Release Lint 与打包通过 |
| `apksigner verify --verbose`、`zipalign -c 4` | 最新 APK 签名与对齐通过；v2、v3 验证成功 |
| 产物清理与差异 | 源目录残留 `.luac` 为 0；git diff --check 通过 |

回归通过 JDK 21 编译，使用应用同版本 luajvm-core-1.0.0.jar 执行。
Java 菜单适配器模拟 Android 菜单接口，真实 LuaJVM 负责双向调用；未在真实 Android Window 中注入事件。
复用既有 Gradle、SDK 和临时仓库镜像配置；JDK 厂商限定文件在构建后已恢复。

## 制品与限制

- Hydrogen_eink_0.6144.apk，8,338,000 字节。
- versionCode 6144，versionName 0.6144-eink。
- SHA-256：39eae5cf9bfc29c743c29249ae788585c191fe24c10769e5a7f2cb45b56d5b12。

B7 Pro 实体按键的实际 keyCode、repeatCount、输入状态与刷新效果仍待实机复测。
设置中的“实体键翻页（墨水屏）”应保持开启。
