package com.qyf.rememberenglish.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.repository.StudyRepository
import com.qyf.rememberenglish.data.repository.WordRepository
import com.qyf.rememberenglish.domain.model.DailyProgress
import com.qyf.rememberenglish.domain.model.QueueItem
import com.qyf.rememberenglish.domain.model.ReviewRating
import com.qyf.rememberenglish.domain.model.Word
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 学习会话状态 */
sealed interface SessionState {
    data object Idle : SessionState
    data class Studying(
        val queue: List<QueueItem>,
        val index: Int,
        val revealed: Boolean,
        val word: Word?,
        val newCount: Int,
        val reviewCount: Int,
    ) : SessionState

    data object Finished : SessionState
}

data class StudyUiState(
    val progress: DailyProgress? = null,
    val session: SessionState = SessionState.Idle,
    /** 空闲时是否有可学内容（决定"开始学习"是否可用） */
    val hasTodayQueue: Boolean = false,
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val studyRepository: StudyRepository,
    private val wordRepository: WordRepository,
) : ViewModel() {

    private val session = MutableStateFlow<SessionState>(SessionState.Idle)
    private val hasTodayQueue = MutableStateFlow(false)

    val uiState: StateFlow<StudyUiState> = combine(
        studyRepository.observeTodayProgress(),
        session,
        hasTodayQueue,
    ) { progress, s, hasQueue ->
        StudyUiState(progress = progress, session = s, hasTodayQueue = hasQueue)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyUiState())

    init {
        refreshQueueAvailability()
    }

    private fun refreshQueueAvailability() {
        viewModelScope.launch {
            hasTodayQueue.value = studyRepository.buildTodayQueue().isNotEmpty()
        }
    }

    fun start() {
        viewModelScope.launch {
            val queue = studyRepository.buildTodayQueue()
            if (queue.isEmpty()) {
                hasTodayQueue.value = false
                return@launch
            }
            session.value = SessionState.Studying(
                queue = queue,
                index = 0,
                revealed = false,
                word = loadWord(queue.first()),
                newCount = 0,
                reviewCount = 0,
            )
        }
    }

    fun reveal() {
        val s = session.value as? SessionState.Studying ?: return
        session.value = s.copy(revealed = true)
    }

    fun rate(rating: ReviewRating) {
        val s = session.value as? SessionState.Studying ?: return
        viewModelScope.launch {
            val item = s.queue[s.index]
            studyRepository.submitReview(item, rating)
            // 不认识 → 当天内重现：排到队尾（CLAUDE.md 第五节）
            val newQueue = if (rating == ReviewRating.AGAIN) s.queue + item else s.queue
            val nextIndex = s.index + 1
            if (nextIndex >= newQueue.size) {
                session.value = SessionState.Finished
                refreshQueueAvailability()
            } else {
                val next = newQueue[nextIndex]
                session.value = s.copy(
                    queue = newQueue,
                    index = nextIndex,
                    revealed = false,
                    word = loadWord(next),
                    newCount = s.newCount + if (item.isNew) 1 else 0,
                    reviewCount = s.reviewCount + if (item.isNew) 0 else 1,
                )
            }
        }
    }

    fun quit() {
        session.value = SessionState.Idle
        refreshQueueAvailability()
    }

    private suspend fun loadWord(item: QueueItem): Word? =
        wordRepository.getWord(item.userWord.wordId)
}
