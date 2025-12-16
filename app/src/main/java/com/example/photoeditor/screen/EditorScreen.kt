package com.example.photoeditor

// REMOVED: Unused import import androidx.compose.ui.platform.LocalDensity
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.opengl.GLSurfaceView
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Lens
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.photoeditor.ui.theme.PhotoEditorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: Uri,
    initialCropRect: Rect? = null,
    onBack: () -> Unit,
    onNavigateToCrop: (Uri, Rect, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 获取 ViewModel
    val viewModel: EditorViewModel = hiltViewModel()
    val currentState = viewModel.currentState

    // 监听裁剪结果
    LaunchedEffect(initialCropRect) {
        if (initialCropRect != null) {
            viewModel.updateCrop(initialCropRect)
        }
    }

    val imageRenderer = remember { ImageRenderer(context) }

    // [修复] 将类型改为 String，并给默认值 "调节" (对应你 BottomBar 里的一个 Tab 名字)
    var selectedMainTab by remember { mutableStateOf("调节") }

    // [修复] 将类型改为 String，并给默认值 "构图" (或者空字符串 "")
    var selectedPrimaryTool by remember { mutableStateOf("构图") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("醒图") },
                // [新增] 这一段是用来改颜色的
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,        // 背景改成黑色 (和底部栏一致)
                    titleContentColor = Color.White,     // 标题文字改白色
                    navigationIconContentColor = Color.White, // 返回箭头改白色
                    actionIconContentColor = Color.White      // 撤销/导出按钮改白色
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        // 返回键
                        Icon(Icons.Default.Close, contentDescription = "Back")
                    }
                },
                actions = {
                    // === 撤销按钮 (用向左箭头暂代) ===
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.canUndo
                    ) {
                        // [修复2] 使用基础图标 ArrowBack (向左)
                        Icon(Icons.Default.ArrowBack, contentDescription = "Undo")
                    }

                    // === 重做按钮 (用向右箭头暂代) ===
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.canRedo
                    ) {
                        // [修复2] 使用基础图标 ArrowForward (向右)
                        Icon(Icons.Default.ArrowForward, contentDescription = "Redo")
                    }

                    // === 导出按钮 ===
                    Button(onClick = {
                        scope.launch {
                            val baseBitmap = if (currentState.cropRect != null) {
                                BitmapUtils.cropImage(context, imageUri, currentState.cropRect!!)
                            } else {
                                BitmapUtils.loadBitmapWithRotation(context, imageUri)
                            }

                            if (baseBitmap != null) {
                                val finalBitmap =
                                    BitmapUtils.applyFilter(baseBitmap, currentState.filterId)

                                val fileName = "Edit_${System.currentTimeMillis()}.jpg"
                                val contentValues = ContentValues().apply {
                                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    put(MediaStore.Images.Media.IS_PENDING, 1)
                                }

                                try {
                                    withContext(Dispatchers.IO) {
                                        val resolver = context.contentResolver
                                        val uri = resolver.insert(
                                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                            contentValues
                                        )
                                        if (uri != null) {
                                            resolver.openOutputStream(uri)?.use { os ->
                                                finalBitmap.compress(
                                                    Bitmap.CompressFormat.JPEG,
                                                    100,
                                                    os
                                                )
                                            }
                                            contentValues.clear()
                                            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                                            resolver.update(uri, contentValues, null, null)

                                            withContext(Dispatchers.Main) {
                                                // [修复1] 这里需要 import android.widget.Toast
                                                Toast.makeText(
                                                    context,
                                                    "保存成功",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                        if (baseBitmap != finalBitmap) baseBitmap.recycle()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    }) {
                        Text("导出")
                    }
                }
            )
        },
        bottomBar = {
            EditorBottomBar(
                selectedPrimaryTool = selectedPrimaryTool,
                selectedMainTab = selectedMainTab,
                currentFilterId = currentState.filterId,
                onFilterSelected = { id -> viewModel.updateFilter(id) },
                onPrimaryToolClick = { toolName ->
                    selectedPrimaryTool = toolName
                    if (toolName == "构图") {
                        // 为了防止 Size 报错，我们先给个默认值
                        // 实际上这里应该用 imageRenderer 里的图片尺寸，但稍微麻烦点
                        // 只要有 Rect 就不影响，因为 cropRect 存的是绝对坐标
                        val defaultRect = Rect(0f, 0f, 1000f, 1000f)
                        val rectToPass = currentState.cropRect ?: defaultRect

                        onNavigateToCrop(imageUri, rectToPass, currentState.filterId)
                    }
                },
                onMainTabClick = { selectedMainTab = it }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {
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
                    imageRenderer.setFilter(currentState.filterId)

                    // === [新增] 连接裁剪预览的线 ===
                    if (currentState.cropRect != null) {
                        // 我们需要获取原图尺寸才能计算比例。
                        // 这里有个小技巧：利用 BitmapFactory 只读取尺寸，不加载图片
                        val options = android.graphics.BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        android.graphics.BitmapFactory.decodeStream(
                            context.contentResolver.openInputStream(imageUri), null, options
                        )
                        val imgW = options.outWidth.toFloat()
                        val imgH = options.outHeight.toFloat()

                        if (imgW > 0 && imgH > 0) {
                            // 把像素坐标 (0,0 - 1080,1920) 转换成 (0.0 - 1.0)
                            val rect = currentState.cropRect
                            val normalizedRect = Rect(
                                left = rect.left / imgW,
                                top = rect.top / imgH,
                                right = rect.right / imgW,
                                bottom = rect.bottom / imgH
                            )
                            // 消除警告：调用这个函数
                            imageRenderer.setNormalizedCropRect(normalizedRect)
                        }
                    } else {
                        // 没裁剪，传 null 还原
                        imageRenderer.setNormalizedCropRect(null)
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
    currentFilterId: Int,          // [新增参数]
    onFilterSelected: (Int) -> Unit, // [新增参数] 回调
    onPrimaryToolClick: (String) -> Unit,
    onMainTabClick: (String) -> Unit
) {
    // 1. 原来的工具列表
    val primaryTools = listOf(
        EditorTool("构图", Icons.Default.Crop),
        EditorTool("局部调整", Icons.Default.Tonality),
        EditorTool("一键出片", Icons.Default.AutoAwesome),
        EditorTool("智能优化", Icons.Default.Camera),
        EditorTool("光感", Icons.Default.WbSunny)
    )

    // 2. [新增] 滤镜列表 (名字, ID)
    val filterOptions = listOf(
        Pair("原图", 0),
        Pair("黑白", 1),
        Pair("暖色", 2),
        Pair("冷色", 3)
    )

    val mainTabs = listOf("AI修图", "调节", "人像", "滤镜", "模板", "贴纸")

    Column(
        modifier = Modifier
            .background(Color.Black)
            .padding(vertical = 8.dp)
    ) {
        // --- 核心修改：这里决定第一排显示什么 ---
        if (selectedMainTab == "滤镜") {
            // === 如果选了“滤镜”，显示滤镜列表 ===
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(filterOptions) { (name, id) ->
                    // 复用 EditorToolButton，图标统一用 Lens 或者 Image
                    EditorToolButton(
                        text = name,
                        icon = Icons.Default.Lens, // 如果没有 Lens，可以用 Image 或 Circle
                        isSelected = currentFilterId == id,
                        onClick = { onFilterSelected(id) }
                    )
                }
            }
        } else {
            // === 没选“滤镜”，显示原来的工具列表 (构图等) ===
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 第二排：主 Tab (保持不变) ---
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
        EditorScreen(imageUri = Uri.EMPTY, onBack = {}, onNavigateToCrop = { _, _, _ -> })
    }
}
