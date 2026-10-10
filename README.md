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

## 功能

- **拍照**：拍照或从相册导入，MobileSAM 自动抠出画面中间的主体，做成白边贴纸；抠错了可以点选别的主体。贴纸带彩虹闪光，用手指往上「揭下来」就存进图鉴。
- **识别**：保存后自动把贴纸发给 DeepSeek 识别中文名、学名、类别和一句简介；没网或没填 Key 时先排队，之后自动补识别。识别错了可以手动改名或点选备选名字。
- **图鉴**：按物种归组，显示遇见次数和初遇日期，可按鸟类/花卉/植物/昆虫/动物筛选。
- **日历**：每天的格子显示当天的贴纸，顶部大卡片展示选中那天的收集。
- **地图**：贴纸钉在拍摄地点上，点一下进详情。
- **贴纸板**：多个画板，贴纸可拖动、双指缩放和旋转，能加文字、手绘、换背景，导出图片到相册。
- **设置**：填 DeepSeek API Key（加密保存）、导出/导入备份。

## MobileSAM 模型

`app/src/main/assets/sam/` 里的两个 ONNX 文件由 `tools/export_mobilesam_onnx.py` 从 [MobileSAM](https://github.com/ChaoningZhang/MobileSAM)（Apache-2.0）官方权重导出，导出后和 PyTorch 原版对比过，掩码 IoU 约 0.98。

## 安装测试版

每次推送代码，GitHub Actions 都会自动打包。打开仓库的 **Actions** 页，点进最新一次成功的 “Android build”，在页面底部的 Artifacts 里下载 `nature-collect-debug-apk`，解压后把 `app-debug.apk` 传到手机上安装（需要允许“安装未知来源应用”）。

## 本地开发

用 Android Studio 打开本目录即可，或者命令行运行 `./gradlew assembleDebug`（需要 JDK 17 和 Android SDK）。
