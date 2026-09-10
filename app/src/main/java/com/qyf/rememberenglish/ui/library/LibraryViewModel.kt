package com.qyf.rememberenglish.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.online.OnlineDictClient
import com.qyf.rememberenglish.data.online.OnlineWord
import com.qyf.rememberenglish.data.repository.SearchHit
import com.qyf.rememberenglish.data.repository.WordRepository
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 在线兜底状态：本地（词库+词组）查不到时自动联网查询 */
sealed interface OnlineLookupState {
    data object Idle : OnlineLookupState
    data object Loading : OnlineLookupState
    data class Found(val word: OnlineWord) : OnlineLookupState
    data object NotFound : OnlineLookupState
}

data class LibraryUiState(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val inMineIds: Set<Long> = emptySet(),
    val online: OnlineLookupState = OnlineLookupState.Idle,
    /** 在线结果已加入我要背 */
    val onlineAdded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val onlineDictClient: OnlineDictClient,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val onlineAdded = MutableStateFlow(false)

    private data class SearchOutcome(
        val hits: List<SearchHit>,
        val online: OnlineLookupState,
    )

    private val searchOutcome = query
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { q ->
            onlineAdded.value = false
            if (q.isBlank()) {
                flowOf(SearchOutcome(emptyList(), OnlineLookupState.Idle))
            } else {
                flow {
                    emit(SearchOutcome(emptyList(), OnlineLookupState.Loading))
                    val hits = wordRepository.search(q)
                    // 本地（词库+词组）无精确匹配 → 联网兜底（用户 2026-09-08 批准；2026-09-10 扩展：
                    // 模糊匹配有近似结果但缺精确词时也联网，如 wifi/serendipity 等未收录词）
                    val normalized = q.trim().lowercase()
                    val online = if (hits.none { it.word.word == normalized }) {
                        val found = onlineDictClient.lookup(normalized)
                        if (found != null) OnlineLookupState.Found(found) else OnlineLookupState.NotFound
                    } else {
                        OnlineLookupState.Idle
                    }
                    emit(SearchOutcome(hits, online))
                }
            }
        }

    private val inMineIds = wordRepository.observeMyWords()
        .map { items -> items.map { it.word.id }.toSet() }

    val uiState: StateFlow<LibraryUiState> = combine(
        query,
        searchOutcome,
        inMineIds,
        onlineAdded,
    ) { q, outcome, ids, added ->
        LibraryUiState(
            query = q,
            results = outcome.hits,
            inMineIds = ids,
            online = outcome.online,
            onlineAdded = added,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun addToMine(wordId: Long) {
        viewModelScope.launch { wordRepository.addToMine(wordId) }
    }

    /** 在线查到的词入库为自定义词并加入我要背 */
    fun addOnlineWordToMine() {
        val online = (uiState.value.online as? OnlineLookupState.Found)?.word ?: return
        viewModelScope.launch {
            val saved = wordRepository.ensureCustomWord(online.word, online.usphone, online.ukphone, online.meanings)
            wordRepository.addToMine(saved.id)
            onlineAdded.value = true
        }
    }
}
