package com.qyf.rememberenglish.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.model.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「我要背」筛选（用户 2026-09-07 定义，按分数而非状态机）：
 * 全部=所有添加的词；新词=只添加过还没背；学习中=已背但未满 5 分；已掌握=满 5 分。
 * 点词进入单词背诵；左滑移出（可撤销）。
 */
enum class MineFilter { ALL, NEW, LEARNING, MASTERED }

data class MyWordsUiState(
    val items: List<Pair<Word, UserWord>> = emptyList(),
    val filter: MineFilter = MineFilter.ALL,
    val total: Int = 0,
    val newCount: Int = 0,
    val learningCount: Int = 0,
    val masteredCount: Int = 0,
    /** 最近左滑移出的词（Screen 侧弹 Snackbar 撤销） */
    val removedWord: Word? = null,
)

@HiltViewModel
class MyWordsViewModel @Inject constructor(
    private val wordRepository: WordRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(MineFilter.ALL)
    private val removedWord = MutableStateFlow<Word?>(null)

    /** 最新列表快照：左滑移出时按 wordId 找到完整词卡供撤销恢复 */
    private var latestItems: List<Pair<Word, UserWord>> = emptyList()
    private var lastRemoved: Pair<Word, UserWord>? = null

    val uiState: StateFlow<MyWordsUiState> = combine(
        wordRepository.observeMyWords(),
        filter,
        removedWord,
    ) { items, f, removed ->
        latestItems = items.map { it.word to it.userWord }
        MyWordsUiState(
            items = latestItems.filter { matches(it.second, f) },
            filter = f,
            total = items.size,
            newCount = items.count { it.userWord.isNew },
            learningCount = items.count { !it.userWord.isNew && !it.userWord.isMastered },
            masteredCount = items.count { it.userWord.isMastered },
            removedWord = removed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyWordsUiState())

    fun setFilter(value: MineFilter) {
        filter.value = value
    }

    /** 左滑移出：记录快照供撤销，随后删除 */
    fun removeFromMine(wordId: Long) {
        val item = latestItems.firstOrNull { it.first.id == wordId } ?: return
        lastRemoved = item
        removedWord.value = item.first
        viewModelScope.launch { wordRepository.removeFromMine(wordId) }
    }

    fun undoRemove() {
        val item = lastRemoved ?: return
        lastRemoved = null
        viewModelScope.launch { wordRepository.restoreUserWord(item.second) }
    }

    /** Snackbar 展示完毕后清除标记，避免旋转屏幕重复弹出 */
    fun consumeRemoved() {
        removedWord.value = null
    }

    private fun matches(userWord: UserWord, f: MineFilter): Boolean = when (f) {
        MineFilter.ALL -> true
        MineFilter.NEW -> userWord.isNew
        // 答过但没满 5 分的（含答错 0 分的）都算学习中
        MineFilter.LEARNING -> !userWord.isNew && !userWord.isMastered
        MineFilter.MASTERED -> userWord.isMastered
    }
}
