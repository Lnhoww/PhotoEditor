package com.example.photoeditor

import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditor.ui.theme.PhotoEditorTheme
import kotlin.math.pow
import android.os.Build
import android.media.ExifInterface
// --- 枚举与辅助函数 ---

enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY
}

/**
 * 根据名称获取长宽比 (Width / Height)
 */
fun getAspectRatio(ratioName: String, imgSize: Size): Float? {
    if (imgSize == Size.Zero) return null
    return when (ratioName) {
        "原比例" -> imgSize.width / imgSize.height
        "1:1" -> 1f
        "3:4" -> 3f / 4f
        "4:3" -> 4f / 3f
        "9:16" -> 9f / 16f
        "16:9" -> 16f / 9f
        else -> null // "自由"
    }
}

// --- 主界面 Composable ---

@Composable
fun CropScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    filterId: Int,
    onConfirm: (Rect) -> Unit
) {
    val context = LocalContext.current
    val imageRenderer = remember { ImageRenderer(context) }

    // 状态变量
    var selectedRatio by remember { mutableStateOf("自由") }
    var selectedFunction by remember { mutableStateOf("裁剪") }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    // imageDisplayRect 这里代表：图片显示区域相对于 GLSurfaceView 左上角的坐标。
    // 因为我们修改了布局，GLSurfaceView 现在是完全贴合图片的，所以 Left/Top 恒为 0。
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }
    var cropRect by remember { mutableStateOf(Rect.Zero) }

    // 拖拽交互状态
    var dragHandle by remember { mutableStateOf(DragHandle.NONE) }
    val handleRadius = with(LocalDensity.current) { 12.dp.toPx() }
    val touchHandleArea = with(LocalDensity.current) { 48.dp.toPx() }
    val minCropSize = handleRadius * 2

    // --- Undo/Redo 历史栈 ---
    val undoStack = remember { mutableStateListOf<Rect>() }
    val redoStack = remember { mutableStateListOf<Rect>() }
    var dragStartRect by remember { mutableStateOf(Rect.Zero) }

    // 辅助：记录操作
    fun recordAction(oldState: Rect) {
        undoStack.add(oldState)
        redoStack.clear()
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val previousRect = undoStack.removeAt(undoStack.lastIndex)
            redoStack.add(cropRect)
            cropRect = previousRect
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val nextRect = redoStack.removeAt(redoStack.lastIndex)
            undoStack.add(cropRect)
            cropRect = nextRect
        }
    }

    // --- 图片尺寸加载 ---
    LaunchedEffect(imageUri) {
        if (imageUri != Uri.EMPTY) {
            try {
                // 1. 获取原始宽高
                var rawWidth = 0f
                var rawHeight = 0f
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    rawWidth = options.outWidth.toFloat()
                    rawHeight = options.outHeight.toFloat()
                }

                // 2. 获取旋转信息
                var rotation = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                        val exif = ExifInterface(inputStream)
                        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                        when (orientation) {
                            ExifInterface.ORIENTATION_ROTATE_90 -> rotation = 90
                            ExifInterface.ORIENTATION_ROTATE_180 -> rotation = 180
                            ExifInterface.ORIENTATION_ROTATE_270 -> rotation = 270
                        }
                    }
                }

                // 3. 如果是 90 或 270 度，交换宽高
                imageSize = if (rotation == 90 || rotation == 270) {
                    Size(rawHeight, rawWidth)
                } else {
                    Size(rawWidth, rawHeight)
                }
            } catch (e: Exception) {
                imageSize = Size.Zero
            }
        }
    }

    Scaffold(
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 1. 中间画布区域 (使用 BoxWithConstraints 进行精确布局)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                val density = LocalDensity.current

                // 直接使用 scope 中的 maxWidth 和 maxHeight (Dp类型)
                // 这样能确保 IDE 识别到我们在使用 BoxWithConstraints 的 scope
                val boxWidth = this.maxWidth
                val boxHeight = this.maxHeight

                // 计算图片在当前屏幕区域下的实际显示尺寸 (Fit Center)
                val displaySize = remember(imageSize, boxWidth, boxHeight) {
                    // 将 Dp 转为 Px
                    val maxWidthPx = with(density) { boxWidth.toPx() }
                    val maxHeightPx = with(density) { boxHeight.toPx() }

                    if (imageSize == Size.Zero || maxWidthPx <= 0 || maxHeightPx <= 0) {
                        Size.Zero
                    } else {
                        val imageRatio = imageSize.width / imageSize.height
                        val containerRatio = maxWidthPx / maxHeightPx
                        if (imageRatio > containerRatio) {
                            // 图片更宽，宽度撑满
                            Size(maxWidthPx, maxWidthPx / imageRatio)
                        } else {
                            // 图片更高，高度撑满
                            Size(maxHeightPx * imageRatio, maxHeightPx)
                        }
                    }
                }

                // 当计算出显示尺寸后，更新 imageDisplayRect 和初始化 cropRect
                LaunchedEffect(displaySize) {
                    if (displaySize != Size.Zero) {
                        // 因为容器会缩小到正好包裹图片，所以 Rect 左上角是 (0,0)
                        imageDisplayRect = Rect(0f, 0f, displaySize.width, displaySize.height)
                        if (cropRect == Rect.Zero) {
                            cropRect = imageDisplayRect
                        }
                    }
                }

                // 只有当尺寸计算完成后才显示内容
                if (displaySize != Size.Zero) {
                    // 这是一个尺寸严格等于图片显示大小的容器
                    Box(
                        modifier = Modifier
                            .size(
                                width = with(density) { displaySize.width.toDp() },
                                height = with(density) { displaySize.height.toDp() }
                            )
                    ) {
                        // 底层：渲染图片
                        // 这里的 fillMaxSize 是填满刚才定义的 Box (即正好是图片大小)，所以 OpenGL 不会拉伸变形
                        AndroidView(
                            factory = { ctx ->
                                GLSurfaceView(ctx).apply {
                                    setEGLContextClientVersion(2)
                                    setRenderer(imageRenderer)
                                    renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                imageRenderer.setImageUri(imageUri, view)
                                imageRenderer.setFilter(filterId)
                            }
                        )

                        // 顶层：绘制裁剪框 & 处理手势
                        Canvas(modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) { //监听手指动作
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        dragStartRect = cropRect    // 1. 记下起始状态，为了后面做“撤回”功能
                                        dragHandle = when {
                                            // 计算手指(startOffset)到左上角(cropRect.topLeft)的距离
                                            // 如果距离小于触摸半径 (touchHandleArea)，就认为你抓住了“左上角”
                                            (startOffset - cropRect.topLeft).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.TOP_LEFT
                                            (startOffset - cropRect.topRight).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.TOP_RIGHT
                                            (startOffset - cropRect.bottomLeft).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.BOTTOM_LEFT
                                            (startOffset - cropRect.bottomRight).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.BOTTOM_RIGHT
                                            // 如果都没按到角，但是按在了矩形内部 -> 认为你想“拖动整张框”
                                            cropRect.contains(startOffset) -> DragHandle.BODY
                                            else -> DragHandle.NONE
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragHandle != DragHandle.NONE && cropRect != dragStartRect) {
                                            recordAction(dragStartRect)
                                        }
                                        dragHandle = DragHandle.NONE
                                    }
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (imageDisplayRect == Rect.Zero) return@detectDragGestures

                                    val targetAspectRatio = getAspectRatio(selectedRatio, imageSize)

                                    if (dragHandle == DragHandle.BODY) {
                                        val newOffsetX = dragAmount.x.coerceIn(
                                            imageDisplayRect.left - cropRect.left,
                                            imageDisplayRect.right - cropRect.right
                                        )
                                        val newOffsetY = dragAmount.y.coerceIn(
                                            imageDisplayRect.top - cropRect.top,
                                            imageDisplayRect.bottom - cropRect.bottom
                                        )
                                        cropRect = cropRect.translate(Offset(newOffsetX, newOffsetY))
                                        return@detectDragGestures
                                    }

                                    if (targetAspectRatio == null) {
                                        // === 自由模式 ===
                                        when (dragHandle) {
                                            DragHandle.TOP_LEFT -> {
                                                val newLeft = (cropRect.left + dragAmount.x).coerceIn(imageDisplayRect.left, cropRect.right - minCropSize)
                                                val newTop = (cropRect.top + dragAmount.y).coerceIn(imageDisplayRect.top, cropRect.bottom - minCropSize)
                                                cropRect = cropRect.copy(left = newLeft, top = newTop)
                                            }
                                            DragHandle.TOP_RIGHT -> {
                                                val newRight = (cropRect.right + dragAmount.x).coerceIn(cropRect.left + minCropSize, imageDisplayRect.right)
                                                val newTop = (cropRect.top + dragAmount.y).coerceIn(imageDisplayRect.top, cropRect.bottom - minCropSize)
                                                cropRect = cropRect.copy(right = newRight, top = newTop)
                                            }
                                            DragHandle.BOTTOM_LEFT -> {
                                                val newLeft = (cropRect.left + dragAmount.x).coerceIn(imageDisplayRect.left, cropRect.right - minCropSize)
                                                val newBottom = (cropRect.bottom + dragAmount.y).coerceIn(cropRect.top + minCropSize, imageDisplayRect.bottom)
                                                cropRect = cropRect.copy(left = newLeft, bottom = newBottom)
                                            }
                                            DragHandle.BOTTOM_RIGHT -> {
                                                val newRight = (cropRect.right + dragAmount.x).coerceIn(cropRect.left + minCropSize, imageDisplayRect.right)
                                                val newBottom = (cropRect.bottom + dragAmount.y).coerceIn(cropRect.top + minCropSize, imageDisplayRect.bottom)
                                                cropRect = cropRect.copy(right = newRight, bottom = newBottom)
                                            }
                                            else -> {}
                                        }
                                    } else {
                                        // === 固定比例模式 ===
                                        when (dragHandle) {
                                            DragHandle.BOTTOM_RIGHT -> {
                                                var newWidth = (cropRect.width + dragAmount.x)
                                                var newHeight = newWidth / targetAspectRatio
                                                if (cropRect.left + newWidth > imageDisplayRect.right) {
                                                    newWidth = imageDisplayRect.right - cropRect.left
                                                    newHeight = newWidth / targetAspectRatio
                                                }
                                                if (cropRect.top + newHeight > imageDisplayRect.bottom) {
                                                    newHeight = imageDisplayRect.bottom - cropRect.top
                                                    newWidth = newHeight * targetAspectRatio
                                                }
                                                if (newWidth >= minCropSize && newHeight >= minCropSize) {
                                                    cropRect = cropRect.copy(right = cropRect.left + newWidth, bottom = cropRect.top + newHeight)
                                                }
                                            }
                                            DragHandle.BOTTOM_LEFT -> {
                                                var newWidth = (cropRect.width - dragAmount.x)
                                                var newHeight = newWidth / targetAspectRatio
                                                if (cropRect.right - newWidth < imageDisplayRect.left) {
                                                    newWidth = cropRect.right - imageDisplayRect.left
                                                    newHeight = newWidth / targetAspectRatio
                                                }
                                                if (cropRect.top + newHeight > imageDisplayRect.bottom) {
                                                    newHeight = imageDisplayRect.bottom - cropRect.top
                                                    newWidth = newHeight * targetAspectRatio
                                                }
                                                if (newWidth >= minCropSize && newHeight >= minCropSize) {
                                                    cropRect = cropRect.copy(left = cropRect.right - newWidth, bottom = cropRect.top + newHeight)
                                                }
                                            }
                                            DragHandle.TOP_RIGHT -> {
                                                var newWidth = (cropRect.width + dragAmount.x)
                                                var newHeight = newWidth / targetAspectRatio
                                                if (cropRect.left + newWidth > imageDisplayRect.right) {
                                                    newWidth = imageDisplayRect.right - cropRect.left
                                                    newHeight = newWidth / targetAspectRatio
                                                }
                                                if (cropRect.bottom - newHeight < imageDisplayRect.top) {
                                                    newHeight = cropRect.bottom - imageDisplayRect.top
                                                    newWidth = newHeight * targetAspectRatio
                                                }
                                                if (newWidth >= minCropSize && newHeight >= minCropSize) {
                                                    cropRect = cropRect.copy(right = cropRect.left + newWidth, top = cropRect.bottom - newHeight)
                                                }
                                            }
                                            DragHandle.TOP_LEFT -> {
                                                var newWidth = (cropRect.width - dragAmount.x)
                                                var newHeight = newWidth / targetAspectRatio
                                                if (cropRect.right - newWidth < imageDisplayRect.left) {
                                                    newWidth = cropRect.right - imageDisplayRect.left
                                                    newHeight = newWidth / targetAspectRatio
                                                }
                                                if (cropRect.bottom - newHeight < imageDisplayRect.top) {
                                                    newHeight = cropRect.bottom - imageDisplayRect.top
                                                    newWidth = newHeight * targetAspectRatio
                                                }
                                                if (newWidth >= minCropSize && newHeight >= minCropSize) {
                                                    cropRect = cropRect.copy(left = cropRect.right - newWidth, top = cropRect.bottom - newHeight)
                                                }
                                            }
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        ) {
                            // 绘制部分：由于Canvas已经就是图片大小了，直接绘制即可，无需再算Offsets
                            if (cropRect != Rect.Zero) {
                                drawRect(
                                    color = Color.White,
                                    topLeft = cropRect.topLeft,
                                    size = cropRect.size,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                                drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.topLeft)
                                drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.topRight)
                                drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.bottomLeft)
                                drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.bottomRight)
                            }
                        }
                    }
                }
            }

            // 2. 底部控制栏
            CropBottomBar(
                selectedRatio = selectedRatio,
                onRatioSelected = { newRatioName ->
                    if (selectedRatio == newRatioName) return@CropBottomBar

                    recordAction(cropRect)
                    selectedRatio = newRatioName

                    val targetRatio = getAspectRatio(newRatioName, imageSize)
                    if (targetRatio != null && imageDisplayRect != Rect.Zero) {
                        // 在当前显示区域(0,0, w, h)内居中计算最大框
                        val displayWidth = imageDisplayRect.width
                        val displayHeight = imageDisplayRect.height
                        val displayRatio = displayWidth / displayHeight

                        val newWidth: Float
                        val newHeight: Float

                        if (targetRatio > displayRatio) {
                            newWidth = displayWidth
                            newHeight = displayWidth / targetRatio
                        } else {
                            newHeight = displayHeight
                            newWidth = displayHeight * targetRatio
                        }

                        val newLeft = (displayWidth - newWidth) / 2
                        val newTop = (displayHeight - newHeight) / 2

                        cropRect = Rect(offset = Offset(newLeft, newTop), size = Size(newWidth, newHeight))
                    }
                },
                selectedFunction = selectedFunction,
                onFunctionSelected = { selectedFunction = it },
                onCancel = onBack,
                onConfirm = {
                    // 1. 计算缩放比例 (原图宽度 / 屏幕显示宽度)
                    // 只要 imageDisplayRect 宽度大于0，就计算比例，否则比例为1
                    val scale = if (imageDisplayRect.width > 0f) {
                        imageSize.width / imageDisplayRect.width
                    } else {
                        1f
                    }

                    // 2. 将屏幕上的裁剪框坐标 * 缩放比例 = 原图上的真实裁剪坐标
                    val realCropRect = Rect(
                        left = cropRect.left * scale,
                        top = cropRect.top * scale,
                        right = cropRect.right * scale,
                        bottom = cropRect.bottom * scale
                    )

                    // 3. 将转换后的 Rect 传出去
                    onConfirm(realCropRect)
                },
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onUndo = { performUndo() },
                onRedo = { performRedo() }
            )
        }
    }
}

// --- 底部控制栏 (保持不变) ---
@Composable
fun CropBottomBar(
    selectedRatio: String,
    onRatioSelected: (String) -> Unit,
    selectedFunction: String,
    onFunctionSelected: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    val ratios = listOf("自由", "原比例", "1:1", "3:4", "4:3", "9:16", "16:9")
    val functions = listOf("裁剪", "扩图", "旋转", "矫正")

    Column(modifier = Modifier.background(Color.Black)) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(ratios) { ratio ->
                Text(
                    text = ratio,
                    color = if (selectedRatio == ratio) Color(0xFF66FF66) else Color.White,
                    modifier = Modifier
                        .clickable { onRatioSelected(ratio) }
                        .padding(horizontal = 16.dp),
                    fontSize = 14.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            functions.forEach { function ->
                Text(
                    text = function,
                    color = if (selectedFunction == function) Color.White else Color.Gray,
                    fontWeight = if (selectedFunction == function) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clickable { onFunctionSelected(function) }
                        .padding(horizontal = 16.dp),
                    fontSize = 16.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (canUndo) Color.White else Color.DarkGray)
                }
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (canRedo) Color.White else Color.DarkGray)
                }
            }

            IconButton(onClick = onConfirm) {
                Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color.White)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CropScreenPreview() {
    PhotoEditorTheme {
        CropScreen(imageUri = Uri.EMPTY, onBack = {},filterId = 0, onConfirm = { _ ->})
    }
}