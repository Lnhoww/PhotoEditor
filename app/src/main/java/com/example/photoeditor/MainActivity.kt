package com.example.photoeditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.* // Import all layout modifiers
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.* // Added for remember and mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import com.example.photoeditor.ui.theme.PhotoEditorTheme

// Define a sealed class to represent different screens in your app
sealed class Screen {
    object Home : Screen()
    object Album : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PhotoEditorTheme {
                // Manage the current screen state
                var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                // Display the appropriate screen based on currentScreen state
                when (currentScreen) {
                    is Screen.Home -> HomeScreen(onNavigateToAlbum = { currentScreen = Screen.Album })
                    is Screen.Album -> AlbumScreen(onBack = { currentScreen = Screen.Home })
                }
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: MainViewModel = viewModel(), onNavigateToAlbum: () -> Unit) {
    val context = LocalContext.current

    val imageLoader = ImageLoader.Builder(context)
        .components { add(GifDecoder.Factory()) }
        .build()

    // State for bottom navigation selection
    var selectedItem by remember { mutableStateOf(0) }
    val items = listOf("修图", "灵感", "我的")
    val icons = listOf(Icons.Default.PhotoCamera, Icons.Default.Lightbulb, Icons.Default.Person)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item) },
                        selected = selectedItem == index,
                        onClick = { selectedItem = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color.White) // Changed background to White
        ) {
            // 1. Top Ad/Banner Area
            ShimmerImageView(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f), // Banner-like aspect ratio
                imageLoader = imageLoader
            )

            // 2. Main Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "导入照片",
                    icon = Icons.Default.Add,
                    backgroundColor = Color.Black,
                    contentColor = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToAlbum // Added navigation onClick
                )
                ActionButton(
                    text = "相机",
                    icon = Icons.Default.CameraAlt,
                    backgroundColor = Color(0xFFC8E6C9), // Light green
                    contentColor = Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }

            // 3. Secondary Action Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    text = "AI修人像",
                    icon = Icons.Default.Face,
                    backgroundColor = Color(0xFFE8F5E9), // Lighter green
                    contentColor = Color.Black,
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "拼图",
                    icon = Icons.Default.GridOn,
                    backgroundColor = Color(0xFFE8F5E9), // Lighter green
                    contentColor = Color.Black,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 4. Quick Tools Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                QuickToolButton(text = "批量修图", icon = Icons.Default.Filter, modifier = Modifier.weight(1f))
                QuickToolButton(text = "画质超清", icon = Icons.Default.Hd, modifier = Modifier.weight(1f))
                QuickToolButton(text = "魔法消除", icon = Icons.Default.AutoFixHigh, modifier = Modifier.weight(1f))
                QuickToolButton(text = "智能抠图", icon = Icons.Default.ContentCut, modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                QuickToolButton(text = "AI修图", icon = Icons.Default.AutoAwesome, modifier = Modifier.weight(1f))
                QuickToolButton(text = "一键消除", icon = Icons.Default.LayersClear, modifier = Modifier.weight(1f))
                QuickToolButton(text = "瘦脸瘦身", icon = Icons.Default.FaceRetouchingNatural, modifier = Modifier.weight(1f))
                QuickToolButton(text = "所有工具", icon = Icons.Default.Apps, modifier = Modifier.weight(1f))
            }


            // Spacer to push future content down
            Spacer(modifier = Modifier.height(24.dp))

            // The "为你推荐" section will be added here in the future
            // (Old media grid will be replaced by a horizontal scrollable list of recommendations)
        }
    }
}

@Composable
fun QuickToolButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color.Black, // Changed icon tint to Black
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = text, fontSize = 12.sp, color = Color.Black) // Changed text color to Black
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    backgroundColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {} // Added onClick parameter with default empty lambda
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onClick() } // Make the button clickable
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = contentColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@Composable
fun ShimmerImageView(modifier: Modifier = Modifier, imageLoader: ImageLoader) {
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.0f),
        Color.White.copy(alpha = 0.4f),
        Color.White.copy(alpha = 0.0f),
    )
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = -500f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "Shimmer Translate"
    )
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim.value - 200, translateAnim.value - 200),
        end = Offset(translateAnim.value, translateAnim.value)
    )
    Box(modifier = modifier) {
        Image(
            painter = painterResource(id = R.drawable.xingtu),
            contentDescription = "Header Image with Shimmer",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush)
        )
        AsyncImage(
            model = "file:///android_asset/Camera.gif",
            contentDescription = "Camera GIF overlay",
            imageLoader = imageLoader,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(0.dp) // Removed 16.dp bottom padding to match screenshot's alignment
                .size(43.dp) // Adjusted size to be smaller as per screenshot
        )
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    PhotoEditorTheme {
        HomeScreen(onNavigateToAlbum = {}) // Provide an empty lambda for preview
    }
}
