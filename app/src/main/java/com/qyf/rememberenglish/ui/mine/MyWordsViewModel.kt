package com.qyf.rememberenglish.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.domain.model.LearningState
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

enum class MineFilter { ALL, NEW, LEARNING, MASTERED }

data class MyWordsUiState(
    val items: List<Pair<Word, UserWord>> = emptyList(),
    val filter: MineFilter = MineFilter.ALL,
    val total: Int = 0,
    val newCount: Int = 0,
    val learningCount: Int = 0,
    val masteredCount: Int = 0,
)

@HiltViewModel
class MyWordsViewModel @Inject constructor(
    private val wordRepository: WordRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(MineFilter.ALL)

    val uiState: StateFlow<MyWordsUiState> = combine(
        wordRepository.observeMyWords(),
        filter,
    ) { items, f ->
        MyWordsUiState(
            items = items.filter { matches(it.userWord, f) }.map { it.word to it.userWord },
            filter = f,
            total = items.size,
            newCount = items.count { it.userWord.state == LearningState.NEW },
            learningCount = items.count {
                it.userWord.state == LearningState.LEARNING ||
                    (it.userWord.state == LearningState.REVIEW && !it.userWord.isMastered)
            },
            masteredCount = items.count { it.userWord.isMastered },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyWordsUiState())

    fun setFilter(value: MineFilter) {
        filter.value = value
    }

    fun removeFromMine(wordId: Long) {
        viewModelScope.launch { wordRepository.removeFromMine(wordId) }
    }

    private fun matches(userWord: UserWord, f: MineFilter): Boolean = when (f) {
        MineFilter.ALL -> true
        MineFilter.NEW -> userWord.state == LearningState.NEW
        MineFilter.LEARNING -> userWord.state == LearningState.LEARNING ||
            (userWord.state == LearningState.REVIEW && !userWord.isMastered)
        MineFilter.MASTERED -> userWord.isMastered
    }
}
