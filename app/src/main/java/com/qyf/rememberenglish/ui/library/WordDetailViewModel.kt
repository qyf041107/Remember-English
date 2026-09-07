package com.qyf.rememberenglish.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.data.speech.TtsPlayer
import com.qyf.rememberenglish.domain.model.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WordDetailUiState(
    val word: Word? = null,
    val inMine: Boolean = false,
)

@HiltViewModel
class WordDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wordRepository: WordRepository,
    val tts: TtsPlayer,
) : ViewModel() {

    val wordId: Long = savedStateHandle.get<Long>("wordId") ?: -1L

    private val word = wordRepository.observeWord(wordId)

    private val inMine = wordRepository.observeMyWords().combine(word) { items, _ ->
        items.any { it.word.id == wordId }
    }

    val uiState: StateFlow<WordDetailUiState> = combine(word, inMine) { w, mine ->
        WordDetailUiState(word = w, inMine = mine)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WordDetailUiState())

    fun toggleMine() {
        viewModelScope.launch {
            if (uiState.value.inMine) wordRepository.removeFromMine(wordId)
            else wordRepository.addToMine(wordId)
        }
    }

    fun speak() {
        uiState.value.word?.let { tts.speak(it.word) }
    }
}
