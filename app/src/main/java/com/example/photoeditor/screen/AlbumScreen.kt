package com.example.photoeditor

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.photoeditor.ui.theme.PhotoEditorTheme
import androidx.hilt.navigation.compose.hiltViewModel
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    onBack: () -> Unit, // 回调：当点击返回按钮时执行
    onImageClick: (Uri) -> Unit, // 回调：当选中一张图时，把图片地址传出去
    viewModel: MainViewModel = hiltViewModel()
) {
    val mediaUris by viewModel.mediaUris.collectAsState()// 通过collectAsState监听信息从MainViewModel中获取到uri地址
    val context = LocalContext.current

    // UI状态
    var selectedFilter by remember { mutableStateOf("图片") }
    val filters = listOf(
        FilterOption("图片", Icons.Default.Image),
        FilterOption("拼图", Icons.Default.Grid4x4),
        FilterOption("批量修图", Icons.Default.Layers)
    )

    // State for album categories
    var selectedCategory by remember { mutableStateOf("全部照片") }
    val categories = listOf("添加画布", "全部照片", "Camera", "醒图", "微信")

    // 1. 适配不同 Android 版本
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    // 2. 注册权限回调
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.loadMedia()// 同意了就加载图片
        } else {
            // 暂时不处理
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地相册", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                filters.forEach { filter ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable { selectedFilter = filter.name }
                            .padding(vertical = 8.dp, horizontal = 16.dp)
                    ) {
                        Icon(imageVector = filter.icon, contentDescription = filter.name)
                        Text(text = filter.name, fontSize = 14.sp)
                        if (selectedFilter == filter.name) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(modifier = Modifier
                                .width(24.dp)
                                .height(2.dp)
                                .background(Color.Black)
                            )
                        }
                    }
                }
            }

            // 2. 横向滚动的胶囊按钮列表
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    ChipButton( //自定义的按钮
                        text = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }

            // 3. 懒加载
            LazyVerticalGrid(
                // 自适应列数：每张图至少 90dp 宽，屏幕越宽列数越多
                columns = GridCells.Adaptive(minSize = 90.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp)
            ) {
                items(mediaUris) { uri ->
                    // MODIFIED: Use ImageRequest to load thumbnails
                    val imageRequest = ImageRequest.Builder(LocalContext.current)
                        .data(uri)
                        .size(256) // 性能优化，只加载缩略图
                        .crossfade(true) // 加载图片淡入的效果
                        .build()

                    AsyncImage(
                        model = imageRequest,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)// 强制正方形 1:1
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onImageClick(uri) },// 点击触发回调
                        contentScale = ContentScale.Crop// 裁剪填满
                    )
                }
            }
        }
    }
}

@Composable
fun ChipButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) Color.Black else Color.White
    val textColor = if (selected) Color.White else Color.Black
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = textColor, fontSize = 14.sp)
    }
}

data class FilterOption(val name: String, val icon: ImageVector)
//UI预览
@Preview(showBackground = true)
@Composable
fun AlbumScreenPreview() {
    PhotoEditorTheme {
        AlbumScreen(onBack = { }, onImageClick = {}) // Provide empty lambdas for preview
    }
}
