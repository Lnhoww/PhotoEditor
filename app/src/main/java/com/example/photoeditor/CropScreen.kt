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

// 定义拖拽手柄枚举
enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY
}

@Composable
fun CropScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onConfirm: (Rect) -> Unit
) {
    val context = LocalContext.current
    val imageRenderer = remember { ImageRenderer(context) }

    var selectedRatio by remember { mutableStateOf("自由") }
    var selectedFunction by remember { mutableStateOf("裁剪") }

    var imageSize by remember { mutableStateOf(Size.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }

    // 当前裁剪框状态
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var dragHandle by remember { mutableStateOf(DragHandle.NONE) }

    // UI 常量
    val handleRadius = with(LocalDensity.current) { 12.dp.toPx() }
    val touchHandleArea = with(LocalDensity.current) { 24.dp.toPx() }
    val minCropSize = handleRadius * 2

    // --- Undo/Redo 逻辑开始 ---
    val undoStack = remember { mutableStateListOf<Rect>() }
    val redoStack = remember { mutableStateListOf<Rect>() }
    var dragStartRect by remember { mutableStateOf(Rect.Zero) } // 记录拖拽前的状态

    // 记录一次操作（将旧状态存入 Undo 栈）
    fun recordAction(oldState: Rect) {
        undoStack.add(oldState)
        redoStack.clear() // 新操作会打断 Redo 历史
    }

    fun performUndo() {
        if (undoStack.isNotEmpty()) {
            // 修改前: val previousRect = undoStack.removeLast()
            // 修改后: 使用 removeAt(lastIndex) 兼容所有 Android 版本
            val previousRect = undoStack.removeAt(undoStack.lastIndex)

            redoStack.add(cropRect) // 把当前状态存入 Redo
            cropRect = previousRect // 恢复状态
        }
    }

    fun performRedo() {
        if (redoStack.isNotEmpty()) {
            // 修改前: val nextRect = redoStack.removeLast()
            // 修改后: 使用 removeAt(lastIndex) 兼容所有 Android 版本
            val nextRect = redoStack.removeAt(redoStack.lastIndex)

            undoStack.add(cropRect)
            cropRect = nextRect
        }
    }
    // --- Undo/Redo 逻辑结束 ---

    // 获取图片原始尺寸
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
            // 1. 中间画布区域
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                // GLSurfaceView 渲染图片
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

                // 裁剪框绘制与交互
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                // 记录操作前的状态
                                dragStartRect = cropRect

                                // 判断点击位置
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
                                // 拖拽结束：如果位置发生了改变，则记录到撤销栈
                                if (dragHandle != DragHandle.NONE && cropRect != dragStartRect) {
                                    recordAction(dragStartRect)
                                }
                                dragHandle = DragHandle.NONE
                            }
                        ) { change, dragAmount ->
                            change.consume()

                            if (imageDisplayRect == Rect.Zero) return@detectDragGestures

                            // 根据手柄位置更新 cropRect
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
                                DragHandle.BODY -> {
                                    val newOffsetX = dragAmount.x.coerceIn(imageDisplayRect.left - cropRect.left, imageDisplayRect.right - cropRect.right)
                                    val newOffsetY = dragAmount.y.coerceIn(imageDisplayRect.top - cropRect.top, imageDisplayRect.bottom - cropRect.bottom)
                                    cropRect = cropRect.translate(Offset(newOffsetX, newOffsetY))
                                }
                                DragHandle.NONE -> { /* Do nothing */ }
                            }
                        }
                    }
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    // 计算图片在 Canvas 中的显示区域 (Center Fit)
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

                        // 初始化 CropRect
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
                        // 绘制四个角的手柄
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
                onRatioSelected = { selectedRatio = it },
                selectedFunction = selectedFunction,
                onFunctionSelected = { selectedFunction = it },
                onCancel = onBack,
                onConfirm = { onConfirm(cropRect) },
                // 传递 Undo/Redo 状态与回调
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                onUndo = { performUndo() },
                onRedo = { performRedo() }
            )
        }
    }
}

@Composable
fun CropBottomBar(
    selectedRatio: String,
    onRatioSelected: (String) -> Unit,
    selectedFunction: String,
    onFunctionSelected: (String) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    // Undo/Redo 参数
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

        // 第三行: 底部操作栏 (包含 Undo/Redo)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 取消按钮
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = "Cancel", tint = Color.White)
            }

            // 中间区域：Undo 和 Redo
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

            // 确认按钮
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