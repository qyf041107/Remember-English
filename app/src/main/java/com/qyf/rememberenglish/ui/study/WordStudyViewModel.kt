package com.qyf.rememberenglish.ui.study

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.StudyRepository
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.domain.model.AnswerRating
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.model.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 单词背诵（"我要背"列表点词进入）：只显示英文 → 点屏显义 → 三键记分 */
data class WordStudyUiState(
    val word: Word? = null,
    val userWord: UserWord? = null,
    val revealed: Boolean = false,
    /** 本次打开已记的分（记过一次即显示结果，防连点重复记分） */
    val rated: AnswerRating? = null,
    val notFound: Boolean = false,
)

@HiltViewModel
class WordStudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wordRepository: WordRepository,
    private val studyRepository: StudyRepository,
) : ViewModel() {

    private val userWordId: Long = savedStateHandle.get<Long>("userWordId") ?: -1L

    private val _ui = MutableStateFlow(WordStudyUiState())
    val ui: StateFlow<WordStudyUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val userWord = wordRepository.getUserWord(userWordId)
            if (userWord == null) {
                _ui.update { it.copy(notFound = true) }
                return@launch
            }
            _ui.update {
                it.copy(word = wordRepository.getWord(userWord.wordId), userWord = userWord)
            }
        }
    }

    fun reveal() {
        _ui.update { it.copy(revealed = true) }
    }

    fun rate(rating: AnswerRating) {
        val current = _ui.value.userWord ?: return
        if (_ui.value.rated != null) return
        viewModelScope.launch {
            val updated = studyRepository.submitAnswer(current, rating)
            // 答错/不清楚 → 自动展开释义让用户看清（用户 2026-09-08 要求）
            _ui.update {
                it.copy(
                    userWord = updated,
                    rated = rating,
                    revealed = it.revealed || rating != AnswerRating.KNOW,
                )
            }
        }
    }
}
