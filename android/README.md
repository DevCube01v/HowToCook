# 下厨指南 Android App

中文 Android 菜谱 App，最低支持 Android 6.0(API 23)。首版包含分类浏览、菜名/食材/做法全文搜索、菜谱详情、收藏和离线阅读。收藏保存在本机，卸载应用后清除。

## 直接获取 APK

无需下载源码或安装本地开发工具。代码进入 GitHub 后，在仓库的 **Actions → Android Debug APK → Run workflow** 中选择分支并运行。成功后打开该次运行，在 **Artifacts** 下载 `HowToCook-debug-运行编号`，解压即可得到可安装的 `HowToCook-debug.apk` 和 `SHA256SUMS.txt`。产物保留 30 天。

新增工作流需要先进入默认分支，GitHub 才会显示手动运行入口。Android 相关的 PR 也会自动构建并上传 APK；来自 fork 的 PR 可能需要仓库管理员批准工作流运行。该工作流不要求 PAT 或发布签名密钥，不会修改仓库或发布 Release。

手机打开 APK 时允许当前下载应用安装未知来源应用即可。Debug 签名仅用于试用；不同云端构建使用不同的临时 Debug 密钥，安装另一次构建可能需要卸载旧版，卸载会清除收藏。正式版本需另外配置稳定的发布签名。

## 内容与离线行为

- 构建时从 `dishes/` 自动收录菜谱，不收录 `dishes/template/` 示例模板；从 `tips/` 收录引用的技巧文档。
- 原始 Markdown 和图片按原始字节打包，生成 HTML 供 WebView 阅读。原菜谱不被修改。难度与热量保持原文。
- 仓库内的图片、菜谱链接、技巧文档链接支持离线打开；Markdown 标题支持锚点。图片保留原尺寸，按屏幕宽度显示。
- 两张外部图片（完美水煮蛋、青椒土豆炒肉）保留原始地址，详情中显示“联网查看原图”，通过系统浏览器打开，不将外部图片下载进离线包。其他外部引用也通过系统浏览器打开。
- 应用不声明网络权限，禁用 WebView JavaScript、文件访问和网络加载，只从受限的 HTTPS 资源入口读取内置资源。
- 每篇详情注明 HowToCook、Anduin2017 与社区贡献者、内容提交版本和当前仓库的原文链接。“来源与许可”页展示 The Unlicense 全文。
- 上游现有的两个失效 Markdown 链接在生成时适配：卤菜的 `糖色.md` 指向 `简易版炒糖色.md`；虎皮肘子的油温文档指向 `tips/advanced/油温判断技巧.md`。原文文件保持不变。其他同名、唯一的已移动文档可以自动解析，缺失或歧义链接导致构建失败。
- 内容更新随新 APK 提供；第一版不提供在线同步。

## 工程和云端构建

工程使用 Android Gradle Plugin 8.9.2、Gradle Wrapper 8.11.1（固定下载校验和）、Java 17 语言级别、Android SDK 35 / Build Tools 35.0.0。GitHub Actions 自动安装 JDK 17、Node.js 22 和 Android SDK。Markdown 渲染依赖单独放在 `tools/`，带锁文件，不改变仓库原有 Node.js 检查流程。

供云端开发或维护运行：

```sh
npm ci --prefix android/tools --ignore-scripts
npm test --prefix android/tools
cd android
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

`preBuild` 会自动生成离线内容，APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。SDK 可由 `ANDROID_HOME` 指定，机器配置、生成的内容和 APK 不进入 Git。

## 验证范围

`tools/build-content.test.js` 检查完整收录、原文与图片字节保留、本地图片及链接可达、失效链接适配、外部图片标识、脚本禁用与来源许可。`RecipeTest` 检查全文搜索、多关键词匹配、分类筛选、空查询和全角输入。

设备冒烟测试位于 `app/src/androidTest/`，使用平台 Instrumentation 验证中文搜索、分类、离线正文与图片、收藏持久化、技巧链接和来源许可。需要可用的云端模拟器：

```sh
./gradlew assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w com.howtocook.app.test/com.howtocook.app.SmokeInstrumentation
```

该测试只在测试过程中临时开启 WebView JavaScript 以读取 DOM；交付应用默认禁用 JavaScript。GitHub Actions 编译此测试 APK，但默认不启动模拟器。

GitHub Actions 上传单元测试与 Lint 报告，交付说明另列云端实际安装和模拟器运行结果。真实手机和不同 Android / WebView 版本的完整兼容性测试仍需后续执行。
