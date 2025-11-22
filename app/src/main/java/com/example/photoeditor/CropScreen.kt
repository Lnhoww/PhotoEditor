package com.example.photoeditor

import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditor.ui.theme.PhotoEditorTheme

@Composable
fun CropScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val imageRenderer = remember { ImageRenderer(context) }
    
    var selectedRatio by remember { mutableStateOf("自由") }
    var selectedFunction by remember { mutableStateOf("裁剪") }

    Scaffold(
        containerColor = Color.Black
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 1. Canvas Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                // Draggable Crop Box Overlay (Placeholder)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    // Draw a simple static rectangle as a placeholder for the crop box
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(50f, 50f),
                        size = Size(canvasWidth - 100f, canvasHeight - 100f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // 2. Bottom Controls
            CropBottomBar(
                selectedRatio = selectedRatio,
                onRatioSelected = { selectedRatio = it },
                selectedFunction = selectedFunction,
                onFunctionSelected = { selectedFunction = it },
                onCancel = onBack,
                onConfirm = onConfirm
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
        CropScreen(imageUri = Uri.EMPTY, onBack = {}, onConfirm = {})
    }
}
