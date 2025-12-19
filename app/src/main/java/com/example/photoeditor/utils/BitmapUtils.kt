package com.example.photoeditor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object BitmapUtils {

    // 预设的最大宽或高 (例如限制在 1080p 或 2K 分辨率)
    // 这样能保证画质足够清晰，又不会撑爆内存
    private const val MAX_WIDTH = 1920
    private const val MAX_HEIGHT = 1920

    // 核心功能：加载图片并根据 EXIF 自动旋转 + **自动采样缩放**
    suspend fun loadBitmapWithRotation(context: Context, uri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            try {
                // === 第一阶段：只读取尺寸，不加载到内存 ===
                inputStream = context.contentResolver.openInputStream(uri)
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true // 关键：只读边框，不读像素
                }
                BitmapFactory.decodeStream(inputStream, null, options)
                inputStream?.close() // 用完立刻关闭

                // === 第二阶段：计算缩放比例 ===
                // 原始尺寸
                val srcWidth = options.outWidth
                val srcHeight = options.outHeight

                // 计算采样率 (inSampleSize)
                // 比如原图 4000px，MAX 是 1000px，那么 inSampleSize = 4
                options.inSampleSize =
                    calculateInSampleSize(srcWidth, srcHeight, MAX_WIDTH, MAX_HEIGHT)

                // === 第三阶段：真正加载图片 (带缩放) ===
                options.inJustDecodeBounds = false // 关键：这次要读像素了
                inputStream = context.contentResolver.openInputStream(uri)
                val sampledBitmap = BitmapFactory.decodeStream(inputStream, null, options)
                    ?: return@withContext null

                // === 第四阶段：处理旋转 ===
                // (这一步和你之前的逻辑一样)
                handleRotation(context, uri, sampledBitmap)

            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                inputStream?.close()
            }
        }
    }

    // 核心功能：根据 Rect 裁剪图片 (也需要防止 OOM)
    suspend fun cropImage(
        context: Context,
        uri: Uri,
        cropRect: androidx.compose.ui.geometry.Rect
    ): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // 这里为了简单，我们还是复用上面的加载逻辑
                // 在商业项目中，这里应该用 BitmapRegionDecoder 来只解码局部区域
                // 但对于实习项目，先用 loadBitmapWithRotation 保证不崩即可
                val originalBitmap = loadBitmapWithRotation(context, uri) ?: return@withContext null

                val safeX = cropRect.left.toInt().coerceIn(0, originalBitmap.width)
                val safeY = cropRect.top.toInt().coerceIn(0, originalBitmap.height)
                val safeWidth = cropRect.width.toInt().coerceAtMost(originalBitmap.width - safeX)
                val safeHeight = cropRect.height.toInt().coerceAtMost(originalBitmap.height - safeY)

                if (safeWidth <= 0 || safeHeight <= 0) return@withContext originalBitmap

                val croppedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    safeX,
                    safeY,
                    safeWidth,
                    safeHeight
                )

                // 记得回收旧图
                if (croppedBitmap != originalBitmap) {
                    // originalBitmap.recycle() // Compose环境有时交给GC处理更安全，暂不强制回收
                }

                croppedBitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun applyEffects(
        originalBitmap: Bitmap,
        filterId: Int,
        brightness: Float,
        contrast: Float,
        saturation: Float
    ): Bitmap {
        // 创建一个可变的 Bitmap 用于绘制
        val newBitmap = Bitmap.createBitmap(
            originalBitmap.width,
            originalBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(newBitmap)
        val paint = android.graphics.Paint()

        // 1. 初始化基础矩阵
        val finalMatrix = android.graphics.ColorMatrix()

        // === A. 先应用滤镜 (和 Shader 逻辑保持一致) ===
        val filterMatrix = android.graphics.ColorMatrix()
        when (filterId) {
            1 -> filterMatrix.setSaturation(0f) // 黑白
            2 -> filterMatrix.set(floatArrayOf( // 暖色
                1f, 0f, 0f, 0f, 25f,
                0f, 1f, 0f, 0f, 12f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            ))
            3 -> filterMatrix.set(floatArrayOf( // 冷色
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 25f,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        finalMatrix.postConcat(filterMatrix)

        // === B. 应用亮度 (Brightness) ===
        // Shader: rgb * brightness
        // Matrix: setScale(r, g, b, a)
        val brightnessMatrix = android.graphics.ColorMatrix()
        brightnessMatrix.setScale(brightness, brightness, brightness, 1f)
        finalMatrix.postConcat(brightnessMatrix)

        // === C. 应用对比度 (Contrast) ===
        // Shader: (color - 0.5) * contrast + 0.5
        // Matrix 原理: color * contrast + (127.5 * (1 - contrast))
        // 因为 Canvas 的 offset 是 0-255，而 Shader 是 0.0-1.0，所以 0.5 对应 127.5
        val contrastMatrix = android.graphics.ColorMatrix()
        val offset = 127.5f * (1 - contrast)
        contrastMatrix.set(floatArrayOf(
            contrast, 0f, 0f, 0f, offset,
            0f, contrast, 0f, 0f, offset,
            0f, 0f, contrast, 0f, offset,
            0f, 0f, 0f, 1f, 0f
        ))
        finalMatrix.postConcat(contrastMatrix)

        // === D. 应用饱和度 (Saturation) ===
        val saturationMatrix = android.graphics.ColorMatrix()
        saturationMatrix.setSaturation(saturation)
        finalMatrix.postConcat(saturationMatrix)

        // 绘制
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(finalMatrix)
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)

        return newBitmap
    }

    // --- 内部辅助算法 ---

    /**
     * 计算采样率
     * 官方文档推荐算法：https://developer.android.com/topic/performance/graphics/load-bitmap
     */
    private fun calculateInSampleSize(
        srcWidth: Int,
        srcHeight: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight: Int = srcHeight / 2
            val halfWidth: Int = srcWidth / 2
            // 每次循环把尺寸除以2，直到宽和高都小于预设值
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * 处理旋转 (抽取出来的逻辑)
     */
    private fun handleRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        var rotation = 0f
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val exif = ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                    rotation = when (orientation) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return if (rotation != 0f) {
            val matrix = Matrix().apply { postRotate(rotation) }
            // 创建新的旋转后的 Bitmap
            val rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            // 如果生成了新对象，最好回收旧的（可选）
            if (rotatedBitmap != bitmap) {
                // bitmap.recycle()
            }
            rotatedBitmap
        } else {
            bitmap
        }
    }
}