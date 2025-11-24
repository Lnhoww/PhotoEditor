package com.example.photoeditor

import android.net.Uri
import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditor.ui.theme.PhotoEditorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: Uri,
    onBack: () -> Unit,
    onNavigateToCrop: (Uri) -> Unit // Added navigation callback for crop
) {
    // State for selected tools
    val selectedPrimaryToolState = remember { mutableStateOf("") }
    var selectedPrimaryTool by selectedPrimaryToolState

    val selectedMainTabState = remember { mutableStateOf("调节") }
    var selectedMainTab by selectedMainTabState

    val context = LocalContext.current
    val imageRenderer = remember { ImageRenderer(context) }

    // Keep track of total scale and translation for gestures
    val scaleState = remember { mutableFloatStateOf(1f) }
    var scale by scaleState

    val offsetXState = remember { mutableFloatStateOf(0f) }
    var offsetX by offsetXState

    val offsetYState = remember { mutableFloatStateOf(0f) }
    var offsetY by offsetYState

    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* No title needed */ },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = { /* Handle export */ }) {
                        Text("导出")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, actionIconContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        bottomBar = {
            EditorBottomBar(
                selectedPrimaryTool = selectedPrimaryTool,
                selectedMainTab = selectedMainTab,
                onPrimaryToolClick = { toolName ->
                    selectedPrimaryTool = toolName
                    if (toolName == "构图") {
                        onNavigateToCrop(imageUri) // Navigate to CropScreen
                    } else {
                        // Reset gestures when changing tools, if desired
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        imageRenderer.setScale(1f)
                        imageRenderer.setTranslation(0f, 0f)
                    }
                },
                onMainTabClick = { selectedMainTab = it }
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box( // This Box will handle the gestures
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures {
                        _, pan, zoom, _ ->
                        scale *= zoom
                        offsetX += pan.x
                        offsetY += pan.y
                        
                        scale = scale.coerceIn(0.5f, 5.0f)

                        imageRenderer.setScale(scale)
                        imageRenderer.setTranslation(offsetX / size.width * 2f, -offsetY / size.height * 2f)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(imageRenderer)
                        // Start with continuous rendering to ensure the first frame is drawn
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    imageRenderer.setImageUri(imageUri, view)
                }
            )
        }
    }
}

@Composable
fun EditorBottomBar(
    selectedPrimaryTool: String,
    selectedMainTab: String,
    onPrimaryToolClick: (String) -> Unit,
    onMainTabClick: (String) -> Unit
) {
    val primaryTools = listOf(
        EditorTool("构图", Icons.Default.Crop),
        EditorTool("局部调整", Icons.Default.Tonality),
        EditorTool("一键出片", Icons.Default.AutoAwesome),
        EditorTool("智能优化", Icons.Default.Camera),
        EditorTool("光感", Icons.Default.WbSunny)
    )

    val mainTabs = listOf("AI修图", "调节", "人像", "滤镜", "模板", "贴纸")

    Column(
        modifier = Modifier
            .background(Color.Black)
            .padding(vertical = 8.dp)
    ) {
        // Bottom Bar Layer 1 (Primary Tools)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(primaryTools) { tool ->
                EditorToolButton(
                    text = tool.name,
                    icon = tool.icon,
                    isSelected = selectedPrimaryTool == tool.name,
                    onClick = { onPrimaryToolClick(tool.name) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Bar Layer 2 (Main Tabs)
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) {
            items(mainTabs) { tab ->
                EditorTabButton(
                    text = tab,
                    isSelected = selectedMainTab == tab,
                    onClick = { onMainTabClick(tab) }
                )
                Spacer(modifier = Modifier.width(24.dp))
            }
        }
    }
}

@Composable
fun EditorToolButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val contentColor = if (isSelected) Color(0xFF66FF66) else Color.White
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = text, color = contentColor, fontSize = 12.sp)
    }
}

@Composable
fun EditorTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color.White else Color.Gray,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(3.dp)
                    .background(Color(0xFF66FF66))
            )
        }
    }
}

data class EditorTool(val name: String, val icon: ImageVector)

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {
    PhotoEditorTheme {
        EditorScreen(imageUri = Uri.EMPTY, onBack = {}, onNavigateToCrop = {})
    }
}
