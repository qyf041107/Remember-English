package com.qyf.rememberenglish.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.domain.model.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordDetailUiState(
    val word: Word? = null,
    val inMine: Boolean = false,
    /** 星标（用户 2026-09-16）：永不算已掌握 + 抽中权重 ×3 */
    val starred: Boolean = false,
    /** 真题词频（无数据 null，CLAUDE.md 第五节） */
    val freq: Int? = null,
)

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wordRepository: WordRepository,
) : ViewModel() {

    val wordId: Long = savedStateHandle.get<Long>("wordId") ?: -1L

    private val word = wordRepository.observeWord(wordId)

    private val inMine = wordRepository.observeMyWords().combine(word) { items, _ ->
        items.any { it.word.id == wordId }
    }

    private val starred = wordRepository.observeStarredWordIds().map { wordId in it }

    val uiState: StateFlow<WordDetailUiState> = combine(word, inMine, starred) { w, mine, star ->
        WordDetailUiState(
            word = w,
            inMine = mine,
            starred = star,
            freq = w?.let { wordRepository.freqOf(it.word) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordDetailUiState())

    fun toggleMine() {
        viewModelScope.launch {
            if (uiState.value.inMine) wordRepository.removeFromMine(wordId)
            else wordRepository.addToMine(wordId)
        }
    }

    /**
     * 星标切换。星标是词卡属性，词不在"我要背"时先自动加入（用户 2026-09-16：
     * 一次点击=我要死磕这个词）；取消星标不会移出"我要背"。
     */
    fun toggleStar() {
        viewModelScope.launch {
            if (uiState.value.starred) {
                wordRepository.setStarred(wordId, false)
            } else {
                wordRepository.starWord(wordId)
            }
        }
    }
}
