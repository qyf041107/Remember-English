package com.qyf.rememberenglish.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 黑名单里的一个词；[meaning] 为词库/自定义词里的首条释义（查不到则为 null） */
data class BlacklistedWord(
    val word: String,
    val meaning: String?,
)

data class BlacklistUiState(
    val words: List<BlacklistedWord> = emptyList(),
    /** 最近移出的词（Snackbar 可撤销） */
    val removedWord: String? = null,
)

/**
 * 词黑名单管理（用户 2026-09-16）：
 * 扫词结果或词库行点 ✕ 拉黑的词都在这里，可查看与恢复。
 * 数据即 DataStore 的 `ocr_ignored_words`（沿用旧 key，改名会丢已有忽略词）。
 */
@HiltViewModel
class BlacklistViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val wordRepository: WordRepository,
) : ViewModel() {

    private val removedWord = MutableStateFlow<String?>(null)

    private val entries = settingsRepository.blacklistFlow.map { words ->
        words.sorted().map { word ->
            BlacklistedWord(word = word, meaning = wordRepository.findByWord(word)?.meanings?.firstOrNull())
        }
    }

    val uiState: StateFlow<BlacklistUiState> = combine(entries, removedWord) { list, removed ->
        BlacklistUiState(words = list, removedWord = removed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BlacklistUiState())

    fun remove(word: String) {
        removedWord.value = word
        viewModelScope.launch { settingsRepository.removeFromBlacklist(word) }
    }

    fun consumeRemoved() {
        removedWord.value = null
    }
}
