package com.qyf.rememberenglish.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.StudyRepository
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.domain.model.AnswerRating
import com.qyf.rememberenglish.domain.model.DailyProgress
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.model.Word
import com.qyf.rememberenglish.domain.select.StudyPicker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 学习会话状态 */
sealed interface SessionState {
    data object Idle : SessionState

    data class Studying(
        val word: Word?,
        val userWord: UserWord,
        /** 是否已点击屏幕显示释义 */
        val revealed: Boolean,
        /** 作答后停留展示释义（答对 1.5s / 答错 3s，可点击跳过），倒计时结束自动进入下一词 */
        val awaitingNext: Boolean = false,
    ) : SessionState

    data object Finished : SessionState
}

data class StudyUiState(
    val progress: DailyProgress? = null,
    val session: SessionState = SessionState.Idle,
    /** 「我要背」里是否有可学内容 */
    val hasWords: Boolean = false,
    /** 停留倒计时秒数（awaitingNext 时 >0） */
    val nextInSeconds: Int = 0,
)

/**
 * 学习会话（CLAUDE.md 第五节）：
 * 从「我要背」加权随机抽词（不会/不清楚的优先，已掌握 0.25 折），
 * 只显示英文，点击屏幕显示释义，三键自评记分；当天背会数达标即完成。
 * 作答后都停留展示中文释义：答对 1.5 秒确认背没背对，答错/不清楚 3 秒看清（均可点击跳过）。
 */
@HiltViewModel
class StudyViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val wordRepository: WordRepository,
) : ViewModel() {

    private val session = MutableStateFlow<SessionState>(SessionState.Idle)
    private val nextInSeconds = MutableStateFlow(0)
    private var waitJob: Job? = null
    private val random = Random.Default

    val uiState: StateFlow<StudyUiState> = combine(
        studyRepository.observeTodayProgress(),
        session,
        wordRepository.observeMyWords(),
        nextInSeconds,
    ) { progress, s, items, seconds ->
        StudyUiState(
            progress = progress,
            session = s,
            // 响应式跟随「我要背」：加词后"随机提问"按钮立即出现
            hasWords = items.any { !it.userWord.isSuspended },
            nextInSeconds = seconds,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState())

    private suspend fun allWords(): List<UserWord> =
        wordRepository.observeMyWords().first().map { it.userWord }.filter { !it.isSuspended }

    fun start() {
        viewModelScope.launch {
            val words = allWords()
            if (words.isEmpty()) return@launch
            val first = StudyPicker.pickNext(words, random) ?: return@launch
            session.value = SessionState.Studying(word = loadWord(first), userWord = first, revealed = false)
        }
    }

    fun reveal() {
        val s = session.value as? SessionState.Studying ?: return
        session.value = s.copy(revealed = true)
    }

    fun rate(rating: AnswerRating) {
        val s = session.value as? SessionState.Studying ?: return
        if (s.awaitingNext) return // 停留期间不重复记分
        viewModelScope.launch {
            studyRepository.submitAnswer(s.userWord, rating)
            // 达成今日目标 → 结束会话
            if (studyRepository.getTodayProgress().isDone) {
                session.value = SessionState.Finished
                return@launch
            }
            // 无论对错都展示中文释义：答对 1.5 秒确认背没背对，答错/不清楚 3 秒看清（用户 2026-09-10）
            val waitMs = if (rating == AnswerRating.KNOW) KNOW_WAIT_MS else WRONG_WAIT_MS
            session.value = s.copy(revealed = true, awaitingNext = true)
            val wordId = s.userWord.id
            waitJob = launch {
                var remainingMs = waitMs
                nextInSeconds.value = displaySeconds(remainingMs)
                while (remainingMs > 0) {
                    if (!isAwaiting(wordId)) return@launch
                    val step = minOf(1_000L, remainingMs)
                    delay(step)
                    remainingMs -= step
                    if (!isAwaiting(wordId)) return@launch
                    if (remainingMs > 0) nextInSeconds.value = displaySeconds(remainingMs)
                }
                advanceAfter(wordId)
            }
        }
    }

    /** 停留期间点击卡片 → 立即进入下一个 */
    fun skipWait() {
        val s = session.value as? SessionState.Studying ?: return
        if (!s.awaitingNext) return
        waitJob?.cancel()
        waitJob = null
        nextInSeconds.value = 0
        viewModelScope.launch { advanceAfter(s.userWord.id) }
    }

    fun quit() {
        waitJob?.cancel()
        waitJob = null
        nextInSeconds.value = 0
        session.value = SessionState.Idle
    }

    private suspend fun advanceAfter(answeredId: Long) {
        nextInSeconds.value = 0
        val words = allWords()
        // 下一题加权随机；尽量不重复刚答过的词（只剩一个词时允许重复）
        val next = StudyPicker.pickNext(words, random, excludeId = answeredId)
            ?: StudyPicker.pickNext(words, random)
        session.value = if (next == null) {
            SessionState.Finished
        } else {
            SessionState.Studying(word = loadWord(next), userWord = next, revealed = false)
        }
    }

    private fun isAwaiting(userWordId: Long): Boolean =
        (session.value as? SessionState.Studying)?.let { it.awaitingNext && it.userWord.id == userWordId } == true

    private suspend fun loadWord(item: UserWord): Word? =
        wordRepository.getWord(item.wordId)

    /** 倒计时展示秒数（向下取整，至少 1——1.5 秒的确认停留显示"1 秒"不夸大） */
    private fun displaySeconds(ms: Long): Int = (ms / 1_000).toInt().coerceAtLeast(1)

    private companion object {
        /** 答对后展示释义确认时长（用户 2026-09-10） */
        const val KNOW_WAIT_MS = 1_500L

        /** 答错/不清楚后看清释义时长（用户 2026-09-08） */
        const val WRONG_WAIT_MS = 3_000L
    }
}
