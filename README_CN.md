# 英语听力 Android APP

原生 Android WebView 浏览器型 APP。

## 已实现功能
- 首页内置 ELLLO、BBC Learning English、VOA Learning English、YouGlish。
- 网页在 APP 内打开。
- 后退 / 前进 / 刷新 / 地址栏。
- 浏览历史永久保存（最多 200 条），可查看与清空。
- Android 系统 DownloadManager 下载。
- 下载保存到 `Downloads/EnglishListening/`。
- 长按普通网页链接，可选择下载、复制或外部浏览器打开。
- 保留 WebView Cookie，可提高登录态下载兼容性。
- Android 8.0+（minSdk 26）。

## 下载限制
仅能下载网页暴露出来的普通 HTTP/HTTPS 文件链接。HLS/DASH 流媒体、`blob:`、DRM、网站明确禁止下载的内容不会绕过限制。

## 构建
项目使用 Android Gradle Plugin 8.11.1、Gradle 8.13、compileSdk 35、Java 17。
在 Android Studio 中打开本文件夹即可构建；或配置 SDK 后执行 `gradle assembleDebug`。

## GitHub 自动生成 APK
项目已包含 `.github/workflows/build-apk.yml`。
上传到 GitHub 的 `main` 分支后会自动构建，也可以在 Actions 页面手动运行 “Build Android APK”。
构建完成后下载名为 `EnglishListening-APK` 的 Artifact，里面的 `app-debug.apk` 可直接安装到 Android 8.0+ 手机。
