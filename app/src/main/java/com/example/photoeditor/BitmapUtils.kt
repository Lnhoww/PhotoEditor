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

    // 核心功能：根据 Rect 裁剪图片，并处理旋转
    suspend fun cropImage(context: Context, uri: Uri, cropRect: androidx.compose.ui.geometry.Rect): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 加载原图（带旋转修正）
                val originalBitmap = loadBitmapWithRotation(context, uri) ?: return@withContext null

                // 2. 计算安全坐标（防止超出边界导致的 Crash）
                val safeX = cropRect.left.toInt().coerceIn(0, originalBitmap.width)
                val safeY = cropRect.top.toInt().coerceIn(0, originalBitmap.height)
                val safeWidth = cropRect.width.toInt().coerceAtMost(originalBitmap.width - safeX)
                val safeHeight = cropRect.height.toInt().coerceAtMost(originalBitmap.height - safeY)

                if (safeWidth <= 0 || safeHeight <= 0) return@withContext originalBitmap

                // 3. 执行原生裁剪（绝对不会出现鬼影）
                val croppedBitmap = Bitmap.createBitmap(
                    originalBitmap,
                    safeX,
                    safeY,
                    safeWidth,
                    safeHeight
                )

                // 内存优化：如果生成了新图，回收旧图（可选，视内存情况而定）
                // if (croppedBitmap != originalBitmap) originalBitmap.recycle()

                croppedBitmap
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // 辅助功能：加载图片并根据 EXIF 自动旋转
    suspend fun loadBitmapWithRotation(context: Context, uri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            var inputStream: InputStream? = null
            try {
                inputStream = context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(inputStream) ?: return@withContext null

                // 读取旋转信息
                var rotation = 0f
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val exif = ExifInterface(stream)
                        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        rotation = when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                            else -> 0f
                        }
                    }
                }

                if (rotation != 0f) {
                    val matrix = Matrix().apply { postRotate(rotation) }
                    Bitmap.createBitmap(originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true)
                } else {
                    originalBitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            } finally {
                inputStream?.close()
            }
        }
    }
}

