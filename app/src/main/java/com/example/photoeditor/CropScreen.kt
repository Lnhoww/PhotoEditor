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

// --- 枚举与辅助函数 ---

enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY
}

/**
 * 根据名称获取长宽比 (Width / Height)
 * @param ratioName 选中的比例名称
 * @param imgSize 图片原始尺寸 (用于计算"原比例")
 * @return 比例 Float 值，如果为 "自由" 则返回 null
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
    onConfirm: (Rect) -> Unit
) {
    val context = LocalContext.current
    val imageRenderer = remember { ImageRenderer(context) }

    // 状态变量
    var selectedRatio by remember { mutableStateOf("自由") }
    var selectedFunction by remember { mutableStateOf("裁剪") }
    var imageSize by remember { mutableStateOf(Size.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) } // 图片在屏幕上的实际显示区域
    var cropRect by remember { mutableStateOf(Rect.Zero) } // 裁剪框相对于 Canvas 的坐标

    // 拖拽交互状态
    var dragHandle by remember { mutableStateOf(DragHandle.NONE) }
    val handleRadius = with(LocalDensity.current) { 12.dp.toPx() }
    val touchHandleArea = with(LocalDensity.current) { 24.dp.toPx() }
    val minCropSize = handleRadius * 2

    // --- Undo/Redo 历史栈 ---
    val undoStack = remember { mutableStateListOf<Rect>() }
    val redoStack = remember { mutableStateListOf<Rect>() }
    var dragStartRect by remember { mutableStateOf(Rect.Zero) } // 拖拽开始时的快照

    // 辅助：记录操作 (存入 Undo，清空 Redo)
    fun recordAction(oldState: Rect) {
        undoStack.add(oldState)
        redoStack.clear()
    }

    // 辅助：执行撤销
    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            val previousRect = undoStack.removeAt(undoStack.lastIndex) // 使用 removeAt 兼容 API 24
            redoStack.add(cropRect)
            cropRect = previousRect
        }
    }

    // 辅助：执行重做
    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            val nextRect = redoStack.removeAt(redoStack.lastIndex) // 使用 removeAt 兼容 API 24
            undoStack.add(cropRect)
            cropRect = nextRect
        }
    }

    // --- 图片尺寸加载 ---
    LaunchedEffect(imageUri) {
        if (imageUri != Uri.EMPTY) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    imageSize = Size(options.outWidth.toFloat(), options.outHeight.toFloat())
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
            // 1. 中间画布区域 (GLSurfaceView + Canvas)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // 底层：渲染图片
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
                    }
                )

                // 顶层：绘制裁剪框 & 处理手势
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                dragStartRect = cropRect // 记住拖拽前的位置

                                // 判断按下的位置是哪个手柄
                                dragHandle = when {
                                    (startOffset - cropRect.topLeft).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.TOP_LEFT
                                    (startOffset - cropRect.topRight).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.TOP_RIGHT
                                    (startOffset - cropRect.bottomLeft).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.BOTTOM_LEFT
                                    (startOffset - cropRect.bottomRight).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.BOTTOM_RIGHT
                                    cropRect.contains(startOffset) -> DragHandle.BODY
                                    else -> DragHandle.NONE
                                }
                            },
                            onDragEnd = {
                                // 拖拽结束：若位置改变，记录历史
                                if (dragHandle != DragHandle.NONE && cropRect != dragStartRect) {
                                    recordAction(dragStartRect)
                                }
                                dragHandle = DragHandle.NONE
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            if (imageDisplayRect == Rect.Zero) return@detectDragGestures

                            val targetAspectRatio = getAspectRatio(selectedRatio, imageSize)

                            // 1. 移动整体 (不涉及比例锁定)
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

                            // 2. 调整大小
                            if (targetAspectRatio == null) {
                                // === 自由模式 (Free) ===
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
                                // === 固定比例模式 (Fixed Aspect Ratio) ===
                                // 逻辑：优先响应 X 轴变化，计算 Y 轴，并进行边界修正
                                when (dragHandle) {
                                    DragHandle.BOTTOM_RIGHT -> {
                                        var newWidth = (cropRect.width + dragAmount.x)
                                        var newHeight = newWidth / targetAspectRatio

                                        // 检查边界
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
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // 每一帧都重新计算图片显示区域 (Center Fit)
                    if (imageSize != Size.Zero) {
                        val imageRatio = imageSize.width / imageSize.height
                        val canvasRatio = canvasWidth / canvasHeight

                        val displayWidth: Float
                        val displayHeight: Float

                        if (imageRatio > canvasRatio) {
                            displayWidth = canvasWidth
                            displayHeight = canvasWidth / imageRatio
                        } else {
                            displayHeight = canvasHeight
                            displayWidth = canvasHeight * imageRatio
                        }

                        val topLeftX = (canvasWidth - displayWidth) / 2
                        val topLeftY = (canvasHeight - displayHeight) / 2

                        imageDisplayRect = Rect(
                            offset = Offset(topLeftX, topLeftY),
                            size = Size(displayWidth, displayHeight)
                        )

                        // 首次初始化裁剪框
                        if (cropRect == Rect.Zero) {
                            cropRect = imageDisplayRect
                        }
                    }

                    // 绘制裁剪框
                    if (cropRect != Rect.Zero) {
                        drawRect(
                            color = Color.White,
                            topLeft = cropRect.topLeft,
                            size = cropRect.size,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // 绘制四个角手柄
                        drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.topLeft)
                        drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.topRight)
                        drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.bottomLeft)
                        drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.bottomRight)
                    }
                }
            }

            // 2. 底部控制栏
            CropBottomBar(
                selectedRatio = selectedRatio,
                onRatioSelected = { newRatioName ->
                    if (selectedRatio == newRatioName) return@CropBottomBar

                    // 切换比例时：记录历史，并重置裁剪框为最大居中框
                    recordAction(cropRect)
                    selectedRatio = newRatioName

                    val targetRatio = getAspectRatio(newRatioName, imageSize)
                    if (targetRatio != null && imageDisplayRect != Rect.Zero) {
                        // 计算符合比例的最大框
                        val displayWidth = imageDisplayRect.width
                        val displayHeight = imageDisplayRect.height
                        val displayRatio = displayWidth / displayHeight

                        val newWidth: Float
                        val newHeight: Float

                        if (targetRatio > displayRatio) {
                            // 目标更宽 -> 宽度撑满
                            newWidth = displayWidth
                            newHeight = displayWidth / targetRatio
                        } else {
                            // 目标更高 -> 高度撑满
                            newHeight = displayHeight
                            newWidth = displayHeight * targetRatio
                        }

                        val newLeft = imageDisplayRect.left + (displayWidth - newWidth) / 2
                        val newTop = imageDisplayRect.top + (displayHeight - newHeight) / 2

                        cropRect = Rect(offset = Offset(newLeft, newTop), size = Size(newWidth, newHeight))
                    }
                },
                selectedFunction = selectedFunction,
                onFunctionSelected = { selectedFunction = it },
                onCancel = onBack,
                onConfirm = { onConfirm(cropRect) },
                // Undo/Redo 参数
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onUndo = { performUndo() },
                onRedo = { performRedo() }
            )
        }
    }
}

// --- 底部控制栏 Composable ---

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
        // 第一行: 比例选择
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

        // 第二行: 功能选择
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

        // 第三行: 操作按钮 (Cancel, Undo/Redo, Confirm)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }

            // Undo / Redo
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = "Undo",
                        tint = if (canUndo) Color.White else Color.DarkGray
                    )
                }

                IconButton(
                    onClick = onRedo,
                    enabled = canRedo
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = "Redo",
                        tint = if (canRedo) Color.White else Color.DarkGray
                    )
                }
            }

            // Confirm
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
        CropScreen(imageUri = Uri.EMPTY, onBack = {}, onConfirm = { _ ->})
    }
}