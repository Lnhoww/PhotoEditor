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

data class MediaItem(
    val uri: Uri,
    val dateAdded: Long
)

class MainViewModel : ViewModel() {

    private val _mediaUris = MutableStateFlow<List<Uri>>(emptyList())
    val mediaUris = _mediaUris.asStateFlow()

    fun loadMedia(context: Context) {
        viewModelScope.launch {
            val mediaItems = loadMediaFromStore(context)
            _mediaUris.value = mediaItems.map { it.uri }
        }
    }

    private suspend fun loadMediaFromStore(context: Context): List<MediaItem> {
        return withContext(Dispatchers.IO) {
            val mediaList = mutableListOf<MediaItem>()

            // Query for images
            val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
            val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                imageProjection,
                null,
                null,
                imageSortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    mediaList.add(MediaItem(uri = contentUri, dateAdded = dateAdded))
                }
            }

            // Query for videos
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
