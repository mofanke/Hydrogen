# 0.6143 全部阅读页面翻页验证

## 原因与范围

此前按键仅由回答页注册，主页列表和承载日报正文的通用网页页没有接入。
本次在 BasePage 提供共用按键与翻页方法，并在 BaseFragment 生命周期注册、移除阅读页面。

覆盖主页推荐、热榜、想法、关注，侧栏日报、收藏与关注列表，以及通用文章、日报正文、网页、本地正文、搜索结果、历史、问题回答和用户内容等原生列表。
工具栏添加“上一屏”“下一屏”箭头，重建菜单后保留入口。
评论列表、日报栏目列表和通用底部弹窗加入实体键翻页。

原生列表按可读高度和可见文字行高计算步长，扣除裁剪、内边距与底部导航遮挡。
末尾沿用当前模型的加载更多；空列表先加载首页；不会在数据返回后自动跳过新条目。
网页继续复用已有正文翻页路径。
返回栈恢复前页；双栏选择焦点所在栏最上层的页面，防止旧焦点翻动被覆盖的列表。
输入文字和打开主页抽屉时保留原控件行为。

## 实际检查

| 检查 | 结果 |
| --- | --- |
| `java -cp /tmp/hydrogen-v3-java-tests:/tmp/hydrogen-luajvm-core.jar PageNavigationTest` | 使用实际 Lua Class、BasePage、BaseFragment、HomeFragment 和 Navigation 代码，替代原生视图与网络模型，验证四个主页栏目、日报/收藏/关注列表、空列表加载、末尾加载、正文、返回栈、双栏多层焦点、按钮重建、弹窗长按和输入保护；通过 |
| `java -cp /tmp/hydrogen-v3-java-tests PageStepTest` | 30 组可读高度与行高测试，逐屏覆盖 200 行、首尾完整、重叠与小视口进度；通过 |
| `java -cp /tmp/hydrogen-v3-java-tests:/tmp/hydrogen-luajvm-core.jar ReadingKeyTest` | 已有回答页按键、反转、开关、宿主路由、重复提交与销毁回归通过 |
| `bash gradlew :app:precompileLuaAssets :app:assembleRelease :app:cleanLuaAssets --no-daemon -Dorg.gradle.jvmargs='-Xmx4g -Dfile.encoding=UTF-8' --console=plain -I /tmp/hydrogen-repositories.init.gradle` | 最终 `BUILD SUCCESSFUL in 18s`；214 个 Lua 文件成功，0 失败；Java 编译、Release Lint 和打包通过 |
| `apksigner verify`、`zipalign -c 4` | 最新 APK 签名与对齐检查通过 |
| APK 内容与清理 | 包含 `page_navigation.luac` 与 `ContentPager`；源目录残留 `.luac` 为 0 |
| `git diff --check` | 通过 |

本次复用前两版的 JDK、Gradle、SDK 与仓库外临时依赖镜像配置。构建时临时移开 JDK 厂商限定文件，完成后已恢复。
本次未修改正文 JS 分页与图片分段算法，保留前版已运行的验证记录；未将旧浏览器与图片测试列为本次运行结果。

## 制品与限制

- 文件：`Hydrogen_eink_0.6143.apk`，8,337,362 字节。
- 版本：`0.6143-eink`，versionCode 为 `6143`。
- SHA-256：`c8b7ea9a680ae3be0d8163393589ace4def8514964578e6ea8e241f0ead88949`。
- 包名与签名沿用前版。

路由测试使用原生视图替身，未在 B7 Pro 或 Android 模拟器实测当前版本。
真实列表裁剪、焦点、数据加载、墨水屏刷新速度和残影仍需实机验证；未登录知乎账号进行在线列表测试。
