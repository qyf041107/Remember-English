package com.qyf.rememberenglish.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.data.speech.TtsPlayer
import com.qyf.rememberenglish.domain.model.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val query: String = "",
    val results: List<Word> = emptyList(),
    val inMineIds: Set<Long> = emptySet(),
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    val tts: TtsPlayer,
) : ViewModel() {

    private val query = MutableStateFlow("")

    private val results = query
        .debounce(200)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            if (q.isBlank()) flow { emit(emptyList<Word>()) } else flow {
                emit(wordRepository.search(q))
            }
        }

    private val inMineIds = wordRepository.observeMyWords()
        .map { items -> items.map { it.word.id }.toSet() }

    val uiState: StateFlow<LibraryUiState> = combine(query, results, inMineIds) { q, r, ids ->
        LibraryUiState(query = q, results = r, inMineIds = ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun addToMine(wordId: Long) {
        viewModelScope.launch { wordRepository.addToMine(wordId) }
    }

    fun speak(word: String) = tts.speak(word)
}
