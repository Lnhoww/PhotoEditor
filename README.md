# 🎨 PhotoEditor - 高性能 Android 图片编辑器

![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-green.svg)
![OpenGL ES](https://img.shields.io/badge/Rendering-OpenGL%20ES%202.0-orange.svg)
![Hilt](https://img.shields.io/badge/DI-Hilt-purple.svg)
![Architecture](https://img.shields.io/badge/Architecture-MVVM-yellow.svg)

> 一个基于 **Jetpack Compose** 和 **OpenGL ES 2.0** 构建的现代化 Android 图片编辑应用。
> 采用 MVVM 架构，专注于**高性能渲染**与**大图内存优化**，实现了媲美商业软件的实时滤镜与交互体验。

---

## ✨ 功能特性 (Features)

* **⚡ 硬件加速渲染**: 基于 OpenGL ES 管线，通过自定义 GLSL 着色器实现毫秒级滤镜处理。
* **🎛️ 专业参数调节**: 支持亮度、对比度、饱和度的实时调节（非叠加图层，基于像素计算）。
* **✂️ 智能构图裁剪**: 独立的裁剪工具，实现屏幕坐标与纹理坐标的精确映射。
* **↩️ 双栈撤销/重做**: 健壮的状态管理系统，支持无限历史记录回溯。
* **👆 丝滑手势交互**: 支持双指无损缩放预览、平移，及**长按对比原图**功能。
* **🚀 4K/8K 大图支持**: 独有的 Bitmap 内存优化策略，彻底解决高分辨率图片的 OOM（内存溢出）问题。

---

## 📱 效果演示 (Demo)

| **实时滤镜与调节** | **智能裁剪与缩放** | **撤销重做与对比** |
|:---:|:---:|:---:|
| <img src="docs/demo_filter.gif" width="240"/> | <img src="docs/demo_crop.gif" width="240"/> | <img src="docs/demo_compare.gif" width="240"/> |

*(注：请等待图片加载，或查看 docs 目录下的高清演示)*

---

## 🛠 技术栈 (Tech Stack)

* **开发语言**: Kotlin (1.9.0+)
* **UI 框架**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material3) —— 声明式 UI 开发
* **架构模式**: MVVM (Model-View-ViewModel) + 单一数据源 (UDF)
* **依赖注入**: [Hilt](https://dagger.dev/hilt/) —— Google 官方推荐的 DI 解决方案
* **异步处理**: Coroutines (协程) & StateFlow
* **图形渲染**:
    * **OpenGL ES 2.0**: 自定义 `GLSurfaceView` 与 Fragment Shaders (片元着色器)
    * **Matrix Math**: 矩阵变换处理图像坐标映射
* **单元测试**: JUnit4, MockK (覆盖核心 ViewModel 逻辑)

---

## 💡 核心技术亮点 (Core Highlights)

### 1. OpenGL ES 硬件加速渲染 (Hardware Acceleration)
摒弃了传统的 CPU `Bitmap` 处理方式，采用 **OpenGL ES 2.0** 渲染管线。
* **自定义 Shader**: 编写原生 **GLSL** 代码，在 GPU 层面并发处理像素点的亮度、对比度和饱和度计算。
* **性能表现**: 即便处理 4000x3000 分辨率的图片，也能保持 **60fps** 的实时预览帧率，无卡顿。

### 2. Bitmap 内存与 OOM 优化 (Memory Optimization)
针对 Android 平台处理高清大图容易崩溃的痛点，设计了完整的加载策略：
* **二次采样加载 (Two-Pass Decoding)**: 利用 `inJustDecodeBounds=true` 预读取尺寸，根据屏幕分辨率动态计算 `inSampleSize`。
* **内存复用**: 严格管理 Bitmap 生命周期，在 OpenGL 纹理上传后及时回收堆内存，确保 App 在低端机型上也能稳定运行。

### 3. 多坐标系映射算法 (Coordinate Mapping)
解决了 Android View 坐标系（左上角原点）与 OpenGL 纹理坐标系（左下角原点）不一致的问题。
* 实现了复杂的矩阵变换逻辑，确保用户在屏幕上绘制的“裁剪框”能精确对应到图片的真实像素区域，不受图片缩放和位移的影响。

### 4. 现代化的状态管理 (State Management)
* **单一可信数据源**: 使用 `EditState` 数据类封装裁剪、滤镜、调节参数等所有状态。
* **不可变性 (Immutability)**: 利用 Kotlin `copy()` 机制配合 `StateFlow`，确保 UI 渲染的线程安全，并轻松实现了撤销/重做功能。

---

## 🏗 架构设计 (Architecture)

遵循 Google 推荐的现代应用架构指南：

```mermaid
graph LR
    A[Repository层] -->|"Bitmap数据"| B(ViewModel层)
    B -->|"UI State (StateFlow)"| C{Compose UI层}
    C -->|"User Intent (事件)"| B
    B -->|"IO 操作"| A
