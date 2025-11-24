# PhotoEditor - 移动图像编辑应用

## 🚀 项目简介

PhotoEditor 是一款基于 Jetpack Compose 和 OpenGL ES 构建的现代化安卓图像编辑应用，旨在提供一个直观、高效的移动照片处理体验。本项目以知名的“醒图”App 布局为灵感，实现了其首页、相册页、图像编辑器。

**核心功能概览：**

*   **首页 (Home Screen)**：动态 Banner、主功能按钮和快速工具栏。
*   **相册页 (Album Screen)**：本地媒体库访问、缩略图展示和动态权限请求。
*   **图像编辑器 (Editor Screen)**：
    *   **OpenGL ES 渲染**：使用 `GLSurfaceView` 实现高性能图像渲染，支持缩放和平移手势。
    *   **裁剪应用**：能够接收裁剪页返回的坐标，并在渲染器中实时应用裁剪效果。
    *   **导出保存**：通过离屏渲染 (FBO) 捕获当前编辑结果（包含缩放、平移和裁剪），并使用 Android MediaStore API 保存为 PNG 文件到用户相册。
*   **裁剪页 (Crop Screen)**：
    *   **自由比例裁剪**：提供一个可拖动的裁剪框，支持四个角和整个框体的自由拖动。
    *   **边界约束**：裁剪框的操作被严格限制在图片的显示区域内，并设置了更大的触摸热区以优化用户体验。
    *   **状态传递**：裁剪完成后，将裁剪区域的坐标信息传回编辑器页。

## 📸 核心功能截图

*   **首页概览**
![77120f50019c82eedf4b4c9a2751a830](https://github.com/user-attachments/assets/3475abcb-f4fd-4231-97f9-14a4649b9690)

*   **相册页概览**
![07aabb8652ce666ac57e50773e47cdfd](https://github.com/user-attachments/assets/bf84df4e-5f3f-4791-92bd-a52c1ef3849e)

*   **图像编辑器 (Editor Screen)**
![0849ad18e2ec80d7ea9f181df4ed0c02](https://github.com/user-attachments/assets/2057b182-e34b-4650-983f-27861c889870)

*   **裁剪页 (Crop Screen)**
   ![1b43b907be05af89ceb8230587256e5e](https://github.com/user-attachments/assets/8c3af152-395e-4ca4-8506-7079063534c7)

    

## 🛠️ 构建与运行说明

### 1. 先决条件

*   **Android Studio Dolphin (2021.1.1) 或更高版本**
*   **Java Development Kit (JDK) 11 或更高版本**
*   **物理安卓设备 或 安卓模拟器** (API Level 24 / Android 7.0 或更高)
*   **Git** (用于克隆仓库)



## 🌟 未来增强

*   **固定比例裁剪**：实现 1:1, 3:4 等固定比例约束下的裁剪逻辑。
*   **缩放和旋转工具**：实现编辑器中除平移缩放外的更多图像处理工具。
*   **滤镜和特效**：集成实际的图像处理滤镜和特效功能。
*   **“为你推荐”模块**：实现首页底部的横向滚动推荐图片流。
*   **登录模块**： 实现用户注册登录功能。
*   **UI 优化**：持续优化 UI 细节，使其与“醒图”App 完全一致。
*   **性能优化**：对大型媒体库的加载和滚动性能进行优化。
