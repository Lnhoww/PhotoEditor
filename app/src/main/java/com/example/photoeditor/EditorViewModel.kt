package com.example.photoeditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel // [新增]
import javax.inject.Inject // [新增]

// 状态快照：记录某一刻的裁剪框和滤镜
data class EditState(
    val cropRect: Rect? = null,
    val filterId: Int = 0
)
@HiltViewModel
class EditorViewModel @Inject constructor() : ViewModel() {

    // 历史记录栈
    private val historyStack = mutableListOf<EditState>()
    private var currentIndex = -1

    // 对外暴露的当前状态
    var currentState by mutableStateOf(EditState())
        private set

    val canUndo: Boolean get() = currentIndex > 0
    val canRedo: Boolean get() = currentIndex < historyStack.size - 1

    init {
        // 初始化第一步：原图状态
        addNewState(EditState())
    }

    // 添加新状态
    private fun addNewState(newState: EditState) {
        while (historyStack.size > currentIndex + 1) {
            historyStack.removeAt(historyStack.lastIndex)
        }
        historyStack.add(newState)
        currentIndex++
        currentState = newState
    }

    // 更新裁剪
    fun updateCrop(newRect: Rect) {
        if (currentState.cropRect != newRect) {
            addNewState(currentState.copy(cropRect = newRect))
        }
    }

    // 更新滤镜
    fun updateFilter(newId: Int) {
        if (currentState.filterId != newId) {
            addNewState(currentState.copy(filterId = newId))
        }
    }

    // 撤销
    fun undo() {
        if (canUndo) {
            currentIndex--
            currentState = historyStack[currentIndex]
        }
    }

    // 重做
    fun redo() {
        if (canRedo) {
            currentIndex++
            currentState = historyStack[currentIndex]
        }
    }
}