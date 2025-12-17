package com.example.photoeditor

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.lifecycle.HiltViewModel // [新增]
import javax.inject.Inject // [新增]
data class MediaItem(
    val uri: Uri,       //图片地址
    val dateAdded: Long //图片添加时间
)
@HiltViewModel
class MainViewModel @Inject constructor(
    //直接注入仓库，不再需要 Context
    private val repository: MediaRepository
): ViewModel() {

    private val _mediaUris = MutableStateFlow<List<Uri>>(emptyList()) //存uri
    val mediaUris = _mediaUris.asStateFlow()

    fun loadMedia() { //查询手机里所有的图片和视频
        viewModelScope.launch {       //启动一个协程在后台
            val mediaItems = repository.getMediaImages()
            _mediaUris.value = mediaItems.map { it.uri }  //从loadMediaFromStore获取的uri存入_mediaUris
        }
    }
}
