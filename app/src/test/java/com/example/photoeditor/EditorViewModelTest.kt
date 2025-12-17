package com.example.photoeditor

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class EditorViewModelTest {

    // 1. 应用刚才写的协程规则
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // 2. 待测试的对象 (System Under Test)
    private val viewModel = EditorViewModel()

    @Test
    fun `initial state should be default`() {
        // 断言：刚创建时，filterId 应该是 0
        assertEquals(0, viewModel.currentState.filterId)
        // 断言：刚创建时，cropRect 应该是 null
        assertNull(viewModel.currentState.cropRect)
        // 断言：一开始不能撤销
        assertFalse(viewModel.canUndo)
    }

    @Test
    fun `updateFilter should change state and enable undo`() {
        // 动作：切换到滤镜 1 (黑白)
        viewModel.updateFilter(1)

        // 验证：当前状态变了
        assertEquals(1, viewModel.currentState.filterId)
        // 验证：历史栈里有东西了，应该可以撤销
        assertTrue(viewModel.canUndo)
    }

    @Test
    fun `undo should revert to previous state`() {
        // 准备：先做两个操作
        // 初始是 0
        viewModel.updateFilter(1) // 变成 1
        viewModel.updateFilter(2) // 变成 2

        assertEquals(2, viewModel.currentState.filterId)

        // 动作：撤销一次
        viewModel.undo()

        // 验证：应该变回 1
        assertEquals(1, viewModel.currentState.filterId)

        // 动作：再撤销一次
        viewModel.undo()

        // 验证：应该变回 0 (初始状态)
        assertEquals(0, viewModel.currentState.filterId)
        assertFalse(viewModel.canUndo) // 到头了，不能撤销了
    }

    @Test
    fun `redo should re-apply state`() {
        // 准备：0 -> 1 -> 撤销回 0
        viewModel.updateFilter(1)
        viewModel.undo()
        assertEquals(0, viewModel.currentState.filterId)
        assertTrue(viewModel.canRedo) // 应该可以重做

        // 动作：重做
        viewModel.redo()

        // 验证：又变成了 1
        assertEquals(1, viewModel.currentState.filterId)
    }

    @Test
    fun `new action should clear redo history`() {
        // 准备：0 -> 1 -> 撤销回 0
        viewModel.updateFilter(1)
        viewModel.undo()

        // 此时如果不重做，而是做了个新操作 (比如变成了 3)
        viewModel.updateFilter(3)

        // 验证：现在的历史应该是 0 -> 3，之前的 1 应该丢了
        assertEquals(3, viewModel.currentState.filterId)

        // 验证：不应该能重做了（因为时间线分叉了）
        assertFalse(viewModel.canRedo)
    }
}