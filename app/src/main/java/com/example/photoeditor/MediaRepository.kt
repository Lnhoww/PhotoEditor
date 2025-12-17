package com.example.photoeditor

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

// [关键 1] @Inject 告诉 Hilt 怎么创建这个仓库
class MediaRepository @Inject constructor(
    // [关键 2] Hilt 自动把 Application 的 Context 注入进来！
    // 你不再需要从 Activity 传 context 了
    @ApplicationContext private val context: Context
) {

    // 这个方法不需要参数了，因为 context 已经在构造函数里了
    suspend fun getMediaImages(): List<MediaItem> {
        return withContext(Dispatchers.IO) {
            val mediaList = mutableListOf<MediaItem>()

            // === 这里的代码几乎全是搬过来的 ===

            val imageProjection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED)
            val imageSortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            // 使用注入进来的 context
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