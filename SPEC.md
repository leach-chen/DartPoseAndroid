# DartPose - 飞镖姿态检测应用

## 1. Project Overview

**Project Name:** DartPose  
**Project Type:** Android Native Application  
**Core Functionality:** 实时检测投飞镖人员的身体姿态，并追踪手部滑动轨迹，绘制运动路径

## 2. Technology Stack & Choices

- **Language:** Kotlin 1.9.0
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 34 (Android 14)
- **UI Framework:** Jetpack Compose
- **Camera:** CameraX 1.3.0
- **ML Framework:** MediaPipe 0.10.9
  - Pose Detection (全身姿态检测)
  - Hand Tracking (手部追踪)
- **Architecture:** MVVM + Clean Architecture
- **DI:** Hilt
- **Async:** Kotlin Coroutines + Flow

## 3. Feature List

1. **实时相机预览** - 使用 CameraX 打开后置相机
2. **全身姿态检测** - 使用 MediaPipe Pose 检测 33 个身体关键点
3. **手部追踪** - 使用 MediaPipe Hand Tracking 检测 21 个手部关键点
4. **姿态可视化** - 在相机画面上绘制身体骨架
5. **手部轨迹绘制** - 追踪手腕关键点，绘制滑动轨迹
6. **轨迹清除** - 支持清除当前轨迹
7. **截图保存** - 保存带轨迹的检测结果

## 4. UI/UX Design Direction

- **Visual Style:** Material Design 3，深色主题适合相机应用
- **Color Scheme:** 
  - 主色：蓝色 (#2196F3)
  - 轨迹颜色：绿色 (#4CAF50)
  - 姿态骨架：青色 (#00BCD4)
- **Layout:** 全屏相机预览，底部控制栏，上帝视角覆盖层显示检测结果
- **Interactions:** 
  - 点击屏幕切换前后相机
  - 底部按钮控制轨迹清除和截图
