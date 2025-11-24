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

enum class DragHandle {
    NONE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, BODY
}

@Composable
fun CropScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onConfirm: (Rect) -> Unit // MODIFIED: onConfirm now accepts a Rect
) {
    val context = LocalContext.current
    val imageRenderer = remember { ImageRenderer(context) }
    
    var selectedRatio by remember { mutableStateOf("自由") }
    var selectedFunction by remember { mutableStateOf("裁剪") }

    var imageSize by remember { mutableStateOf(Size.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }

    // State for the crop rectangle
    var cropRect by remember { mutableStateOf(Rect.Zero) }
    var dragHandle by remember { mutableStateOf(DragHandle.NONE) }
    val handleRadius = with(LocalDensity.current) { 12.dp.toPx() }
    val touchHandleArea = with(LocalDensity.current) { 24.dp.toPx() } // Larger touch area for handles
    val minCropSize = handleRadius * 2

    // Get original image dimensions without loading the full bitmap
    LaunchedEffect(imageUri) {
        if (imageUri != Uri.EMPTY) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    imageSize = Size(options.outWidth.toFloat(), options.outHeight.toFloat())
                }
            } catch (e: Exception) {
                // Handle exception
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
            // 1. Canvas Area
            // Added padding around the Box containing GLSurfaceView and Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 8.dp), // Added padding here
                contentAlignment = Alignment.Center
            ) {
                // GLSurfaceView for rendering the image
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
                // Draggable Crop Box Overlay
                Canvas(modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                dragHandle = when {
                                    (startOffset - cropRect.topLeft).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.TOP_LEFT
                                    (startOffset - cropRect.topRight).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.TOP_RIGHT
                                    (startOffset - cropRect.bottomLeft).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.BOTTOM_LEFT
                                    (startOffset - cropRect.bottomRight).getDistanceSquared() < touchHandleArea.pow(2) -> DragHandle.BOTTOM_RIGHT
                                    cropRect.contains(startOffset) -> DragHandle.BODY
                                    else -> DragHandle.NONE
                                }
                            },
                            onDragEnd = { dragHandle = DragHandle.NONE }
                        ) { change, dragAmount ->
                            change.consume()
                            
                            if (imageDisplayRect == Rect.Zero) return@detectDragGestures

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
                        
                        if (cropRect == Rect.Zero) {
                             cropRect = imageDisplayRect
                        }
                    }

                    // Draw the crop rectangle only if it has been initialized
                    if (cropRect != Rect.Zero) {
                        drawRect(
                            color = Color.White,
                            topLeft = cropRect.topLeft,
                            size = cropRect.size,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Draw handles
                        drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.topLeft)
                        drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.topRight)
                        drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.bottomLeft)
                        drawCircle(Color.White, radius = handleRadius / 2, center = cropRect.bottomRight)
                    }
                }
            }

            // 2. Bottom Controls
            CropBottomBar(
                selectedRatio = selectedRatio,
                onRatioSelected = { selectedRatio = it },
                selectedFunction = selectedFunction,
                onFunctionSelected = { selectedFunction = it },
                onCancel = onBack,
                onConfirm = { onConfirm(cropRect) } // MODIFIED: Pass cropRect on confirm
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
    onConfirm: () -> Unit
) {
    val ratios = listOf("自由", "原比例", "1:1", "3:4", "4:3", "9:16", "16:9")
    val functions = listOf("裁剪", "扩图", "旋转", "矫正")

    Column(modifier = Modifier.background(Color.Black)) {
        // Layer 1: Ratios
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

        // Layer 2: Functions
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

        // Layer 3: Confirm/Cancel
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
