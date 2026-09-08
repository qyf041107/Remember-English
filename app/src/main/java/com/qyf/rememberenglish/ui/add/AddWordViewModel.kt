package com.qyf.rememberenglish.ui.add

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.qyf.rememberenglish.data.ocr.ImageEnhancer
import com.qyf.rememberenglish.data.online.BaiduHandwritingClient
import com.qyf.rememberenglish.data.online.OnlineDictClient
import com.qyf.rememberenglish.data.repository.AddWordsResult
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.data.settings.SettingsRepository
import com.qyf.rememberenglish.domain.model.Word
import com.qyf.rememberenglish.domain.ocr.WordExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** 候选词：命中词库显示释义；未命中为自定义词（加入时入库）；freq=真题词频（无数据 null） */
data class CandidateWord(
    val text: String,
    val matched: Word?,
    val freq: Int? = null,
)

/** 候选词排序（CLAUDE.md 第五节，用户 2026-09-07 要求考频优先）：词频降序 → 词库命中 → 未命中 */
private val CANDIDATE_ORDER = compareBy<CandidateWord>(
    { it.freq == null },
    { -(it.freq ?: 0) },
    { it.matched == null },
)

data class AddWordUiState(
    val candidates: List<CandidateWord> = emptyList(),
    /** 已勾选的单词（字符串唯一） */
    val selected: Set<String> = emptySet(),
    /** 冻结识别（点画面暂停，方便勾选） */
    val frozen: Boolean = false,
    /** 手写增强（灰度+对比度拉伸），实时识别与拍照/相册共用 */
    val enhance: Boolean = false,
    /** 拍照/相册图片识别中 */
    val recognizing: Boolean = false,
    /** 图片识别失败（Screen 弹 Snackbar 后 consume） */
    val photoFailed: Boolean = false,
    /** 云端识别失败已回退本地（Screen 提示后 consume） */
    val cloudFailed: Boolean = false,
    /** 拍照识别出的未收录词 → 联网查到的释义（空列表=联网也没查到） */
    val onlineMeanings: Map<String, List<String>> = emptyMap(),
    /** 正在联网查询释义的词 */
    val onlineLoading: Set<String> = emptySet(),
    val adding: Boolean = false,
    /** 最近一次加入结果（Screen 侧用资源字符串格式化） */
    val result: AddWordsResult? = null,
)

@HiltViewModel
class AddWordViewModel @Inject constructor(
    private val wordRepository: WordRepository,
    private val settingsRepository: SettingsRepository,
    private val baiduHandwritingClient: BaiduHandwritingClient,
    private val onlineDictClient: OnlineDictClient,
) : ViewModel() {

    private val _ui = MutableStateFlow(AddWordUiState())
    val ui: StateFlow<AddWordUiState> = _ui.asStateFlow()

    private var lastRawText = ""
    private val photoRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** 拍照候选词联网查释义：会话内缓存（未查到也缓存，防重复请求），并发限 4 */
    private val onlineMeaningCache = mutableMapOf<String, List<String>>()
    private val lookupSemaphore = Semaphore(4)

    /** 实时识别回调：提取 → 词库匹配 → 更新候选（保留已勾选） */
    fun onOcrText(rawText: String) {
        if (_ui.value.frozen || rawText == lastRawText) return
        lastRawText = rawText
        val tokens = WordExtractor.extract(rawText)
        if (tokens.isEmpty()) return
        viewModelScope.launch {
            // 考频优先排序（CLAUDE.md 第五节）：有真题词频的按词频降序在前，
            // 词库命中但无词频的次之，未命中的自定义词最后；组内保持画面出现顺序
            val candidates = tokens.map { token ->
                CandidateWord(
                    text = token,
                    matched = wordRepository.findByWord(token),
                    freq = wordRepository.freqOf(token),
                )
            }.sortedWith(CANDIDATE_ORDER)
            _ui.update { it.copy(candidates = candidates) }
        }
    }

    /**
     * 拍照/相册静态识别（手写词录入主路径，用户 2026-09-08）：
     * 云端手写识别开关开启且密钥已填 → 优先百度手写 OCR（识别率更高），
     * 失败自动回退本地 ML Kit；本地路径下手写增强开启时先做灰度+对比度拉伸。
     * 结果整批替换候选列表并冻结实时流（点画面恢复）。
     */
    fun recognizePhoto(bitmap: Bitmap, rotationDegrees: Int) {
        if (_ui.value.recognizing) return
        _ui.update { it.copy(recognizing = true, cloudFailed = false) }
        viewModelScope.launch {
            // 云端优先（用户 2026-09-08：离线模型手写正确率不够）
            val settings = settingsRepository.settingsFlow.first()
            if (settings.cloudOcrEnabled) {
                val upright = if (rotationDegrees != 0) rotate(bitmap, rotationDegrees) else bitmap
                val text = runCatching {
                    baiduHandwritingClient.recognize(settings.baiduApiKey, settings.baiduSecretKey, upright)
                }.getOrNull()
                if (text != null) {
                    onPhotoText(text)
                    _ui.update { it.copy(recognizing = false) }
                    return@launch
                }
                _ui.update { it.copy(cloudFailed = true) }
            }

            // 本地回退：ML Kit + 可选手写增强
            val prepared = if (_ui.value.enhance) {
                withContext(Dispatchers.Default) { ImageEnhancer.enhance(bitmap) }
            } else {
                bitmap
            }
            photoRecognizer.process(InputImage.fromBitmap(prepared, rotationDegrees))
                .addOnSuccessListener { result -> onPhotoText(result.text) }
                .addOnFailureListener {
                    _ui.update { state -> state.copy(photoFailed = true) }
                }
                .addOnCompleteListener {
                    _ui.update { state -> state.copy(recognizing = false) }
                }
        }
    }

    /** 拍照代理帧未带 EXIF，云端识别前先转正 */
    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun onPhotoText(rawText: String) {
        val tokens = WordExtractor.extract(rawText)
        if (tokens.isEmpty()) return
        viewModelScope.launch {
            val mapped = tokens.map { token ->
                CandidateWord(
                    text = token,
                    matched = wordRepository.findByWord(token),
                    freq = wordRepository.freqOf(token),
                )
            }.sortedWith(CANDIDATE_ORDER)
            val missing = mapped.filter { it.matched == null && it.text !in onlineMeaningCache }
            _ui.update { state ->
                state.copy(
                    candidates = mapped,
                    selected = state.selected intersect mapped.map { it.text }.toSet(),
                    frozen = true,
                    // 已缓存的在线释义立即回填，未缓存的标记为查询中
                    onlineMeanings = state.onlineMeanings + missing.mapNotNull { candidate ->
                        onlineMeaningCache[candidate.text]?.let { candidate.text to it }
                    }.toMap(),
                    onlineLoading = missing.map { it.text }.toSet(),
                )
            }
            // 未收录词联网查释义（用户 2026-09-08 要求：拍照界面也要有中文释义）
            missing.forEach { candidate -> lookupOnline(candidate.text) }
        }
    }

    /** 单个未收录词联网查释义；结果（含空）写入缓存并刷新 UI */
    private fun lookupOnline(token: String) {
        viewModelScope.launch {
            lookupSemaphore.withPermit {
                val meanings = runCatching {
                    onlineDictClient.lookup(token)?.meanings
                }.getOrNull().orEmpty()
                onlineMeaningCache[token] = meanings
                _ui.update { state ->
                    state.copy(
                        onlineMeanings = state.onlineMeanings + (token to meanings),
                        onlineLoading = state.onlineLoading - token,
                    )
                }
            }
        }
    }

    fun toggleSelect(text: String) {
        _ui.update { state ->
            state.copy(
                selected = if (text in state.selected) state.selected - text else state.selected + text,
            )
        }
    }

    fun addSelected() {
        val selected = _ui.value.selected.toList()
        if (selected.isEmpty()) return
        addWords(selected)
    }

    private fun addWords(words: List<String>) {
        _ui.update { it.copy(adding = true) }
        viewModelScope.launch {
            val result: AddWordsResult = wordRepository.addWords(words)
            _ui.update { state ->
                state.copy(
                    adding = false,
                    selected = emptySet(),
                    candidates = state.candidates.filterNot { it.text in words },
                    result = result,
                )
            }
        }
    }

    fun consumeMessage() {
        _ui.update { it.copy(result = null) }
    }

    fun notifyPhotoFailed() {
        _ui.update { it.copy(photoFailed = true) }
    }

    fun consumePhotoFailed() {
        _ui.update { it.copy(photoFailed = false) }
    }

    fun consumeCloudFailed() {
        _ui.update { it.copy(cloudFailed = false) }
    }

    fun toggleFrozen() {
        lastRawText = ""
        _ui.update { it.copy(frozen = !it.frozen) }
    }

    fun toggleEnhance() {
        _ui.update { it.copy(enhance = !it.enhance) }
    }

    override fun onCleared() {
        photoRecognizer.close()
    }
}
