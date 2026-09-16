package com.qyf.rememberenglish.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.online.OnlineDictClient
import com.qyf.rememberenglish.data.online.OnlineWord
import com.qyf.rememberenglish.data.online.SpellSuggestClient
import com.qyf.rememberenglish.data.repository.SearchHit
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.data.settings.SettingsRepository
import com.qyf.rememberenglish.domain.search.SpellSuggestionFilter
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 在线兜底状态：本地（词库+词组）查不到时自动联网查询 */
sealed interface OnlineLookupState {
    data object Idle : OnlineLookupState
    data object Loading : OnlineLookupState
    data class Found(val word: OnlineWord) : OnlineLookupState

    /**
     * 拼错了：本地与联网精确查都没结果，但拼写建议接口给了候选（用户 2026-09-16）。
     * [original] 是用户输入的错拼词，[words] 是纠正后并已回填完整释义的词。
     */
    data class Suggestions(val original: String, val words: List<OnlineWord>) : OnlineLookupState

    data object NotFound : OnlineLookupState
}

data class LibraryUiState(
    val query: String = "",
    val results: List<SearchHit> = emptyList(),
    val inMineIds: Set<Long> = emptySet(),
    /** 已星标的词条 id（用户 2026-09-16） */
    val starredIds: Set<Long> = emptySet(),
    val online: OnlineLookupState = OnlineLookupState.Idle,
    /** 在线结果已加入我要背 */
    val onlineAdded: Boolean = false,
    /** 在线结果已星标 */
    val onlineStarred: Boolean = false,
    /** 拼写建议行已加入"我要背"（按单词文本记，建议词一般尚未入库） */
    val suggestionAdded: Set<String> = emptySet(),
    /** 拼写建议行已星标 */
    val suggestionStarred: Set<String> = emptySet(),
    /** 查询词在黑名单里——不明说用户会以为搜索坏了 */
    val queryBlacklisted: Boolean = false,
    /** 刚拉黑的词（Snackbar 可撤销，null=无） */
    val blacklistedWord: String? = null,
    /** 点星标时顺带加入了"我要背"的词（提示一次，null=无） */
    val starNotice: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val onlineDictClient: OnlineDictClient,
    private val spellSuggestClient: SpellSuggestClient,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** 搜索之外的一次性 UI 状态，聚在一起免得 combine 塞九个流 */
    private data class Flags(
        val onlineAdded: Boolean = false,
        val onlineStarred: Boolean = false,
        val suggestionAdded: Set<String> = emptySet(),
        val suggestionStarred: Set<String> = emptySet(),
        val queryBlacklisted: Boolean = false,
        val blacklistedWord: String? = null,
        val starNotice: String? = null,
    )

    private data class SearchOutcome(
        val hits: List<SearchHit>,
        val online: OnlineLookupState,
    )

    private val query = MutableStateFlow("")
    private val flags = MutableStateFlow(Flags())

    /** 黑名单变化后强制重搜（distinctUntilChanged 会吃掉同值查询） */
    private val refreshTick = MutableStateFlow(0)

    private val searchOutcome = combine(query.debounce(250).distinctUntilChanged(), refreshTick) { q, _ -> q }
        .flatMapLatest { q ->
            if (q.isBlank()) {
                flags.update { it.copy(queryBlacklisted = false).clearedOnlineMarks() }
                flowOf(SearchOutcome(emptyList(), OnlineLookupState.Idle))
            } else {
                flow {
                    emit(SearchOutcome(emptyList(), OnlineLookupState.Loading))
                    // 一次搜索开始：把四类标记**全部**重建（此前只清 online 两项，
                    // 导致 suggestion 的 ✓/★ 跨查询残留，搜完一个词再搜另一个会凭空显示）
                    flags.update { it.copy(queryBlacklisted = false).clearedOnlineMarks() }
                    val hits = wordRepository.search(q)
                    val normalized = q.trim().lowercase()
                    // 黑名单词不再从联网"溜回来"，否则词库行的"加入黑名单"看起来毫无作用（用户 2026-09-16）
                    val blacklisted = wordRepository.isBlacklisted(normalized)
                    flags.update { it.copy(queryBlacklisted = blacklisted) }
                    // 本地（词库+词组）无精确匹配 → 联网兜底（用户 2026-09-08 批准；2026-09-10 扩展：
                    // 模糊匹配有近似结果但缺精确词时也联网，如 wifi/serendipity 等未收录词）
                    val online = if (!blacklisted && hits.none { it.word.word == normalized }) {
                        val found = onlineDictClient.lookup(normalized)
                        when {
                            found != null -> OnlineLookupState.Found(found)
                            // 精确查也失败 → 当作拼错了，用拼写建议纠错（用户 2026-09-16）
                            else -> suggestWords(normalized)
                        }
                    } else {
                        OnlineLookupState.Idle
                    }
                    // 标记必须从库里读，不能靠本地标志（用户 2026-09-16 实测踩到）：
                    // 本地搜索只查 source IN (0,2)，**不含自定义词**，所以"当年从在线结果加入过、
                    // 已有分数"的词再搜时，在线行会错误显示 + ——照旧逻辑点 + 再点 ✓ 就删掉了有分数的词卡。
                    backfillMineMarks(online)
                    emit(SearchOutcome(hits, online))
                }
            }
        }

    /** 清掉四条"已加入/已星标"标记（在线行 + 拼写建议行） */
    private fun Flags.clearedOnlineMarks() = copy(
        onlineAdded = false,
        onlineStarred = false,
        suggestionAdded = emptySet(),
        suggestionStarred = emptySet(),
    )

    /** 按**单词文本**回填"已加入/已星标"（在线词此时可能还没进 dict_word，拿不到 wordId，只能按文本查） */
    private suspend fun backfillMineMarks(online: OnlineLookupState) {
        when (online) {
            is OnlineLookupState.Found -> {
                val word = listOf(online.word.word)
                flags.update {
                    it.copy(
                        onlineAdded = wordRepository.findMineWords(word).isNotEmpty(),
                        onlineStarred = wordRepository.findStarredWords(word).isNotEmpty(),
                    )
                }
            }
            is OnlineLookupState.Suggestions -> {
                val words = online.words.map { it.word }
                flags.update {
                    it.copy(
                        suggestionAdded = wordRepository.findMineWords(words),
                        suggestionStarred = wordRepository.findStarredWords(words),
                    )
                }
            }
            else -> Unit
        }
    }

    private val inMineIds = wordRepository.observeMyWords()
        .map { items -> items.map { it.word.id }.toSet() }

    private val starredIds = wordRepository.observeStarredWordIds()
        .map { it.toSet() }

    val uiState: StateFlow<LibraryUiState> = combine(
        query,
        searchOutcome,
        inMineIds,
        starredIds,
        flags,
    ) { q, outcome, ids, starred, f ->
        LibraryUiState(
            query = q,
            results = outcome.hits,
            inMineIds = ids,
            starredIds = starred,
            online = outcome.online,
            onlineAdded = f.onlineAdded,
            onlineStarred = f.onlineStarred,
            suggestionAdded = f.suggestionAdded,
            suggestionStarred = f.suggestionStarred,
            queryBlacklisted = f.queryBlacklisted,
            blacklistedWord = f.blacklistedWord,
            starNotice = f.starNotice,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    /**
     * 加入 / 取消加入（用户 2026-09-16：词库里误点了 + 要能再点一次撤回）。
     * 取消走既有删除语义，会连分数与星标一起清掉——用户已确认只用它撤销"刚误加"。
     */
    fun toggleMine(wordId: Long) {
        viewModelScope.launch { wordRepository.toggleMine(wordId) }
    }

    /** 词库行星标切换：未加入"我要背"时先自动加入再打星（用户 2026-09-16） */
    fun toggleStar(wordId: Long, word: String) {
        val starred = wordId in uiState.value.starredIds
        viewModelScope.launch {
            if (starred) {
                wordRepository.setStarred(wordId, false)
            } else if (wordRepository.starWord(wordId)) {
                flags.update { it.copy(starNotice = word) }
            }
        }
    }

    /** 词库行拉黑：持久化 + 从结果里消失，Snackbar 可撤销 */
    fun blacklistWord(word: String) {
        viewModelScope.launch { settingsRepository.addToBlacklist(word) }
        flags.update { it.copy(blacklistedWord = word) }
        refreshTick.update { it + 1 }
    }

    fun unblacklistWord(word: String) {
        viewModelScope.launch {
            settingsRepository.removeFromBlacklist(word)
            refreshTick.update { it + 1 }
        }
    }

    fun consumeBlacklistedWord() {
        flags.update { it.copy(blacklistedWord = null) }
    }

    fun consumeStarNotice() {
        flags.update { it.copy(starNotice = null) }
    }

    /**
     * 点开在线结果（用户 2026-09-16 反馈"在线结果点不开"）：
     * 先落库为自定义词拿到 wordId，再复用现有词详情页——布局/加入我要背/星标全部现成，
     * 也不必处理加载态与重查失败。代价是浏览过的在线词在 dict_word 留一行 source=1（词库页不显示）。
     */
    fun openOnlineWordDetail(onReady: (Long) -> Unit) {
        val online = currentOnlineWord() ?: return
        viewModelScope.launch { onReady(ensureOnlineWord(online)) }
    }

    /** 在线查到的词入库为自定义词并加入我要背 */
    fun addOnlineWordToMine() {
        val online = currentOnlineWord() ?: return
        viewModelScope.launch {
            wordRepository.addToMine(ensureOnlineWord(online))
            flags.update { it.copy(onlineAdded = true) }
        }
    }

    /** 在线结果行：加入 / 取消加入切换 */
    fun toggleOnlineMine() {
        val online = currentOnlineWord() ?: return
        viewModelScope.launch {
            val wordId = ensureOnlineWord(online)
            val nowInMine = wordRepository.toggleMine(wordId)
            flags.update { it.copy(onlineAdded = nowInMine) }
        }
    }

    /** 拼写建议行：加入 / 取消加入切换 */
    fun toggleSuggestionMine(word: String) {
        viewModelScope.launch {
            val nowInMine = wordRepository.toggleMineByText(word)
            flags.update {
                it.copy(
                    suggestionAdded = if (nowInMine) {
                        it.suggestionAdded + word
                    } else {
                        it.suggestionAdded - word
                    },
                )
            }
        }
    }

    /** 在线结果星标：先落库（自定义词）再打星；未加入"我要背"时一并加入 */
    fun starOnlineWord() {
        val online = currentOnlineWord() ?: return
        viewModelScope.launch {
            val wordId = ensureOnlineWord(online)
            if (flags.value.onlineStarred) {
                wordRepository.setStarred(wordId, false)
                flags.update { it.copy(onlineStarred = false) }
            } else {
                val newlyAdded = wordRepository.starWord(wordId)
                flags.update {
                    it.copy(
                        onlineStarred = true,
                        onlineAdded = it.onlineAdded || newlyAdded,
                        starNotice = if (newlyAdded) online.word else it.starNotice,
                    )
                }
            }
        }
    }

    /** 在线结果拉黑 */
    fun blacklistOnlineWord() {
        val online = currentOnlineWord() ?: return
        viewModelScope.launch { settingsRepository.addToBlacklist(online.word) }
        flags.update { it.copy(blacklistedWord = online.word, queryBlacklisted = true) }
        // 必须重搜才会消失（用户 2026-09-16 反馈"拉黑了还显示在上面"）：
        // 与本地行 blacklistWord、建议行 blacklistSuggestion 保持一致——只有 refreshTick 变了
        // 才会重跑 search()，届时 isBlacklisted 为真 → online 变 Idle → 那一行才移除。
        refreshTick.update { it + 1 }
    }

    // ---- 拼写建议行（用户 2026-09-16）：与在线结果行同样的三件套 ----

    /** 拼写建议行：加入我要背 */
    fun addSuggestionToMine(word: String) {
        viewModelScope.launch {
            wordRepository.addWords(listOf(word))
            flags.update { it.copy(suggestionAdded = it.suggestionAdded + word) }
        }
    }

    /** 拼写建议行：星标切换（未入库时先加入再打星） */
    fun toggleSuggestionStar(word: String) {
        viewModelScope.launch {
            if (word in flags.value.suggestionStarred) {
                wordRepository.unstarWordByText(word)
                flags.update { it.copy(suggestionStarred = it.suggestionStarred - word) }
            } else {
                val newlyAdded = wordRepository.starWordByText(word)
                flags.update {
                    it.copy(
                        suggestionStarred = it.suggestionStarred + word,
                        suggestionAdded = if (newlyAdded) it.suggestionAdded + word else it.suggestionAdded,
                        starNotice = if (newlyAdded) word else it.starNotice,
                    )
                }
            }
        }
    }

    /** 拼写建议行：拉黑（重搜后不会再从建议里回来，见 suggestWords 的黑名单过滤） */
    fun blacklistSuggestion(word: String) {
        viewModelScope.launch { settingsRepository.addToBlacklist(word) }
        flags.update { it.copy(blacklistedWord = word) }
        refreshTick.update { it + 1 }
    }

    /** 点拼写建议行 → 落库后进详情页（与在线结果同一路径） */
    fun openSuggestionDetail(word: String, onReady: (Long) -> Unit) {
        viewModelScope.launch {
            onReady(wordRepository.ensureCustomWord(word).id)
        }
    }

    private fun currentOnlineWord(): OnlineWord? =
        (uiState.value.online as? OnlineLookupState.Found)?.word

    /**
     * 精确查也失败 → 用有道拼写建议纠错（用户 2026-09-16）。
     * 建议词必须**再查一次 jsonapi 回填完整释义**：suggest 返回的 explain 带 "..." 截断且无音标。
     */
    private suspend fun suggestWords(query: String): OnlineLookupState {
        val candidates = spellSuggestClient.suggest(query).map { it.word }
        val picked = SpellSuggestionFilter.filter(query, candidates)
            .filterNot { wordRepository.isBlacklisted(it) }
        val words = picked.mapNotNull { onlineDictClient.lookup(it) }
        return if (words.isEmpty()) {
            OnlineLookupState.NotFound
        } else {
            OnlineLookupState.Suggestions(original = query, words = words)
        }
    }

    /** 在线词落库为自定义词（source=1），返回词条 id；幂等，重复调用复用同一行 */
    private suspend fun ensureOnlineWord(online: OnlineWord): Long =
        wordRepository.ensureCustomWord(
            online.word,
            online.usphone,
            online.ukphone,
            online.meanings,
        ).id
}
