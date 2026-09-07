package com.qyf.rememberenglish.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.AddWordsResult
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.domain.model.Word
import com.qyf.rememberenglish.domain.ocr.WordExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 候选词：命中词库显示释义；未命中为自定义词（加入时入库） */
data class CandidateWord(
    val text: String,
    val matched: Word?,
)

data class AddWordUiState(
    val candidates: List<CandidateWord> = emptyList(),
    /** 已勾选的单词（字符串唯一） */
    val selected: Set<String> = emptySet(),
    val manualInput: String = "",
    /** 冻结识别（点画面暂停，方便勾选） */
    val frozen: Boolean = false,
    val adding: Boolean = false,
    /** 最近一次加入结果（Screen 侧用资源字符串格式化） */
    val result: AddWordsResult? = null,
)

@HiltViewModel
class AddWordViewModel @Inject constructor(
    private val wordRepository: WordRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AddWordUiState())
    val ui: StateFlow<AddWordUiState> = _ui.asStateFlow()

    private var lastRawText = ""

    /** 实时识别回调：提取 → 词库匹配 → 更新候选（保留已勾选） */
    fun onOcrText(rawText: String) {
        if (_ui.value.frozen || rawText == lastRawText) return
        lastRawText = rawText
        val tokens = WordExtractor.extract(rawText)
        if (tokens.isEmpty()) return
        viewModelScope.launch {
            // 考研词库命中的排前面（Kotlin 排序稳定，组内保持画面出现顺序），未命中的自定义词排后面
            val candidates = tokens.map { token ->
                CandidateWord(text = token, matched = wordRepository.findByWord(token))
            }.sortedByDescending { it.matched != null }
            _ui.update { it.copy(candidates = candidates) }
        }
    }

    fun toggleSelect(text: String) {
        _ui.update { state ->
            state.copy(
                selected = if (text in state.selected) state.selected - text else state.selected + text,
            )
        }
    }

    fun setManualInput(value: String) {
        _ui.update { it.copy(manualInput = value) }
    }

    /** 手动输入加入：与 OCR 同一管线（命中→加词；未命中→自定义词） */
    fun addManual() {
        val input = _ui.value.manualInput.trim().lowercase()
        if (input.isEmpty()) return
        addWords(listOf(input), clearInput = true)
    }

    fun addSelected() {
        val selected = _ui.value.selected.toList()
        if (selected.isEmpty()) return
        addWords(selected, clearInput = false)
    }

    private fun addWords(words: List<String>, clearInput: Boolean) {
        _ui.update { it.copy(adding = true) }
        viewModelScope.launch {
            val result: AddWordsResult = wordRepository.addWords(words)
            _ui.update { state ->
                state.copy(
                    adding = false,
                    selected = emptySet(),
                    manualInput = if (clearInput) "" else state.manualInput,
                    candidates = state.candidates.filterNot { it.text in words },
                    result = result,
                )
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(result = null) }
    }

    fun toggleFrozen() {
        lastRawText = ""
        _ui.update { it.copy(frozen = !it.frozen) }
    }
}
