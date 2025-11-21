package com.example.photoeditor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.photoeditor.ui.theme.PhotoEditorTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    imageUri: Uri,
    onBack: () -> Unit
) {
    // State for selected tools
    var selectedPrimaryTool by remember { mutableStateOf("一键出片") }
    var selectedMainTab by remember { mutableStateOf("调节") }

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
                onPrimaryToolClick = { selectedPrimaryTool = it },
                onMainTabClick = { selectedMainTab = it }
            )
        },
        containerColor = Color.Black
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Middle canvas area
            AsyncImage(
                model = imageUri,
                contentDescription = "Image to edit",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
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
    val contentColor = if (isSelected) Color(0xFF66FF66) else Color.White // A bright green for selection
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
                    .background(Color(0xFF66FF66)) // A bright green for selection
            )
        }
    }
}

data class EditorTool(val name: String, val icon: ImageVector)

@Preview(showBackground = true)
@Composable
fun EditorScreenPreview() {
    PhotoEditorTheme {
        // Use a placeholder URI for the preview
        EditorScreen(imageUri = Uri.EMPTY, onBack = {})
    }
}
