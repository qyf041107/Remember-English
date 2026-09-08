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
        /** 答错/不清楚后停留展示释义，倒计时后自动进入下一词（用户 2026-09-08 要求） */
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
 * 答错/不清楚：停留当前词展示释义 3 秒（可点击跳过）再进入下一词。
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
            if (rating == AnswerRating.KNOW) {
                advanceAfter(s.userWord.id)
            } else {
                // 我不会/不清楚：先展示中文释义 3 秒，让用户看清意思再走
                session.value = s.copy(revealed = true, awaitingNext = true)
                waitJob = launch {
                    nextInSeconds.value = NEXT_WAIT_SECONDS
                    while (nextInSeconds.value > 1) {
                        delay(1_000)
                        if (!isAwaiting(s.userWord.id)) return@launch
                        nextInSeconds.value -= 1
                    }
                    delay(1_000)
                    if (isAwaiting(s.userWord.id)) advanceAfter(s.userWord.id)
                }
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

    private companion object {
        const val NEXT_WAIT_SECONDS = 3
    }
}
