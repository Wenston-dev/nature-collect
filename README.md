# 自然收集

拍一张照片，自动把小动物、小鸟、植物和花草抠成贴纸，识别出它的名字，收进自己的图鉴。安卓 app，界面参考 CapLog。

## 技术方案

| 环节 | 方案 |
| --- | --- |
| 开发框架 | Kotlin + Jetpack Compose + CameraX，最低 Android 8.0 |
| 主体抠图 | MobileSAM（ONNX Runtime，手机本地运行） |
| 物种识别 | DeepSeek V4.1 Flash（`deepseek-flash`，在 app 设置里填自己的 API Key） |
| 地图 | MapLibre + OpenFreeMap |
| 存储 | Room + 本地图片文件 |

整个 app 不依赖 Google Play 服务。

## 进度

- [x] 项目骨架：五个页面（贴纸板、拍照、日历、地图、图鉴）和底部导航
- [ ] v0.1：拍照/相册导入、抠图贴纸、物种识别、图鉴、日历
- [ ] v0.2：地图、备份导出/导入
- [ ] v0.3：贴纸板

## 安装测试版

每次推送代码，GitHub Actions 都会自动打包。打开仓库的 **Actions** 页，点进最新一次成功的 “Android build”，在页面底部的 Artifacts 里下载 `nature-collect-debug-apk`，解压后把 `app-debug.apk` 传到手机上安装（需要允许“安装未知来源应用”）。

## 本地开发

用 Android Studio 打开本目录即可，或者命令行运行 `./gradlew assembleDebug`（需要 JDK 17 和 Android SDK）。
