package com.example.photoeditor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.opengl.GLSurfaceView
import android.provider.MediaStore
import android.content.ContentValues
import java.io.OutputStream
import java.io.IOException
import java.util.UUID // For unique file names
import kotlinx.coroutines.launch // ADDED: Import for coroutineScope.launch

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
// REMOVED: Unused import import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.photoeditor.ui.theme.PhotoEditorTheme
// REMOVED: Unused imports import kotlin.math.max, import kotlin.math.min
// REMOVED: Unused import import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: Uri,
    initialCropRect: Rect? = null,
    onBack: () -> Unit,
    onNavigateToCrop: (Uri, Rect) -> Unit
) {
    val selectedPrimaryToolState = remember { mutableStateOf("") }
    var selectedPrimaryTool by selectedPrimaryToolState

    val selectedMainTabState = remember { mutableStateOf("调节") }
    var selectedMainTab by selectedMainTabState

    val context = LocalContext.current
    val imageRenderer = remember { ImageRenderer(context) }

    val scaleState = remember { mutableFloatStateOf(1f) }
    var scale by scaleState

    val offsetXState = remember { mutableFloatStateOf(0f) }
    var offsetX by offsetXState

    val offsetYState = remember { mutableFloatStateOf(0f) }
    var offsetY by offsetYState

    var imageSize by remember { mutableStateOf(Size.Zero) }
    var imageDisplayRect by remember { mutableStateOf(Rect.Zero) }
    var glSurfaceViewSize by remember { mutableStateOf(IntSize.Zero) }

    val appliedCropRectState = remember { mutableStateOf<Rect?>(initialCropRect) }
    var appliedCropRect by appliedCropRectState

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(initialCropRect) {
        if (initialCropRect != null) {
            appliedCropRect = initialCropRect
            scale = 1f
            offsetX = 0f
            offsetY = 0f
            imageRenderer.setScale(1f)
            imageRenderer.setTranslation(0f, 0f)
        }
    }

    LaunchedEffect(imageUri) {
        if (imageUri != Uri.EMPTY) {
            try {
                context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(inputStream, null, options)
                    imageSize = Size(options.outWidth.toFloat(), options.outHeight.toFloat())
                }
            } catch (_: Exception) {
                imageSize = Size.Zero
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { /* No title needed */ },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = { 
                        imageRenderer.surfaceView?.queueEvent { 
                            val bitmap = imageRenderer.captureRenderedBitmap()
                            if (bitmap != null) {
                                val fileName = "PhotoEditor_" + UUID.randomUUID().toString() + ".png"
                                val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                                    put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                                }

                                try {
                                    val resolver = context.contentResolver
                                    val imageUri = resolver.insert(imageCollection, contentValues)
                                    if (imageUri != null) {
                                        val outputStream: OutputStream? = resolver.openOutputStream(imageUri)
                                        outputStream?.use { os ->
                                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                                            coroutineScope.launch { snackbarHostState.showSnackbar("图片已保存到相册") }
                                            Unit
                                        }
                                    } else {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("保存失败: 无法创建文件") }
                                    }
                                } catch (_: IOException) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("保存失败: IO错误") }
                                } catch (_: Exception) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("保存失败: 未知错误") }
                                }
                            } else {
                                coroutineScope.launch { snackbarHostState.showSnackbar("保存失败: 无法捕获图片") }
                            }
                        }
                    }) {
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
                        val rectToPass = appliedCropRect ?: imageDisplayRect
                        // MODIFIED: Removed check for Rect.Zero to ensure navigation always happens
                        onNavigateToCrop(imageUri, rectToPass)
                    } else {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        imageRenderer.setScale(1f)
                        imageRenderer.setTranslation(0f, 0f)
                        appliedCropRect = null
                    }
                },
                onMainTabClick = { selectedMainTab = it }
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .onSizeChanged { size ->
                    glSurfaceViewSize = size
                    if (imageSize != Size.Zero && size.width > 0 && size.height > 0) {
                        val canvasWidth = size.width.toFloat()
                        val canvasHeight = size.height.toFloat()
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
                    } else if (size.width > 0 && size.height > 0) { // MODIFIED: Initialize imageDisplayRect even if imageSize is Zero
                         // Fallback to fill the entire GLSurfaceView if image size is unknown
                         imageDisplayRect = Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures {
                        _, pan, zoom, _ ->
                        scale *= zoom
                        offsetX += pan.x
                        offsetY += pan.y
                        
                        scale = scale.coerceIn(0.5f, 5.0f)

                        imageRenderer.setScale(scale)
                        imageRenderer.setTranslation(
                            (offsetX / glSurfaceViewSize.width.toFloat()) * 2f,
                            -(offsetY / glSurfaceViewSize.height.toFloat()) * 2f
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    GLSurfaceView(ctx).apply {
                        setEGLContextClientVersion(2)
                        setRenderer(imageRenderer)
                        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    imageRenderer.setImageUri(imageUri, view)
                    imageRenderer.setScale(scale)
                    imageRenderer.setTranslation(
                        (offsetX / glSurfaceViewSize.width.toFloat()) * 2f,
                        -(offsetY / glSurfaceViewSize.height.toFloat()) * 2f
                    )

                    if (appliedCropRect != null && glSurfaceViewSize != IntSize.Zero) {
                        val glLeft = (appliedCropRect!!.left / glSurfaceViewSize.width.toFloat()) * 2f - 1f
                        val glTop = 1f - (appliedCropRect!!.top / glSurfaceViewSize.height.toFloat()) * 2f
                        val glRight = (appliedCropRect!!.right / glSurfaceViewSize.width.toFloat()) * 2f - 1f
                        val glBottom = 1f - (appliedCropRect!!.bottom / glSurfaceViewSize.height.toFloat()) * 2f
                        val glCropRect = Rect(glLeft, glTop, glRight, glBottom)
                        imageRenderer.setAppliedCropRect(glCropRect)
                    } else {
                        imageRenderer.setAppliedCropRect(null)
                    }
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
        EditorScreen(imageUri = Uri.EMPTY, onBack = {}, onNavigateToCrop = { _, _ ->})
    }
}
