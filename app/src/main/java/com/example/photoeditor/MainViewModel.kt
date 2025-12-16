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
class MainViewModel @Inject constructor(): ViewModel() {  //手机旋转时存的数据还在

    private val _mediaUris = MutableStateFlow<List<Uri>>(emptyList()) //存uri
    val mediaUris = _mediaUris.asStateFlow()

    fun loadMedia(context: Context) { //查询手机里所有的图片和视频
        viewModelScope.launch {       //启动一个协程在后台
            val mediaItems = loadMediaFromStore(context)
            _mediaUris.value = mediaItems.map { it.uri }  //从loadMediaFromStore获取的uri存入_mediaUris
        }
    }

    private suspend fun loadMediaFromStore(context: Context): List<MediaItem> {
        return withContext(Dispatchers.IO) { //后台线程，防止卡死主界面
            val mediaList = mutableListOf<MediaItem>()

            // 查询图片
            val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED) //获取图片id和时间
            val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"                              //按添加时间倒叙

            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                imageSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)              //id列号
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)//时间列号
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)                  //获取id
                    val dateAdded = cursor.getLong(dateAddedColumn)    //获取时间
                    val contentUri = ContentUris.withAppendedId(            //拼接uri和id获得具体图片地址
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    mediaList.add(MediaItem(uri = contentUri, dateAdded = dateAdded))
                }
            }

            // 查询视频
            val videoProjection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DATE_ADDED)
            val videoSortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                videoProjection,
                null,
                null,
                videoSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    mediaList.add(MediaItem(uri = contentUri, dateAdded = dateAdded))
                }
            }

            // Sort the combined list
            mediaList.sortByDescending { it.dateAdded }
            mediaList
        }
    }
}
