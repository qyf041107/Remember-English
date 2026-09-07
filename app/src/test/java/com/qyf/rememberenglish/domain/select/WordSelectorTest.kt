package com.qyf.rememberenglish.domain.select

import com.qyf.rememberenglish.domain.model.LearningState
import com.qyf.rememberenglish.domain.model.UserWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSelectorTest {

    private fun word(
        id: Long,
        addedAt: Long,
        dueAt: Long,
        state: LearningState = LearningState.REVIEW,
        suspended: Boolean = false,
    ) = UserWord(
        id = id, wordId = id, addedAt = addedAt, state = state,
        ease = 2.5, intervalDays = 1.0, reps = 1, lapses = 0, streak = 1,
        dueAt = dueAt, isSuspended = suspended, isMastered = false,
    )

    @Test
    fun `到期复习优先且按dueAt升序`() {
        val due = listOf(
            word(1, 0, dueAt = 300),
            word(2, 0, dueAt = 100),
            word(3, 0, dueAt = 200),
        )
        val queue = WordSelector.buildQueue(due, emptyList(), remainingNewTarget = 0)
        assertEquals(listOf(2L, 3L, 1L), queue.map { it.userWord.wordId })
        assertTrue(queue.none { it.isNew })
    }

    @Test
    fun `新词按加入顺序且受剩余目标限制`() {
        val news = listOf(
            word(1, addedAt = 30, dueAt = 0, state = LearningState.NEW),
            word(2, addedAt = 10, dueAt = 0, state = LearningState.NEW),
            word(3, addedAt = 20, dueAt = 0, state = LearningState.NEW),
        )
        val queue = WordSelector.buildQueue(emptyList(), news, remainingNewTarget = 2)
        assertEquals(listOf(2L, 3L), queue.map { it.userWord.wordId })
        assertTrue(queue.all { it.isNew })
    }

    @Test
    fun `复习在前新词在后`() {
        val due = listOf(word(1, 0, dueAt = 100))
        val news = listOf(word(2, 0, dueAt = 0, state = LearningState.NEW))
        val queue = WordSelector.buildQueue(due, news, remainingNewTarget = 5)
        assertEquals(listOf(false, true), queue.map { it.isNew })
    }

    @Test
    fun `挂起词被过滤`() {
        val due = listOf(word(1, 0, 100, suspended = true))
        val news = listOf(word(2, 0, 0, LearningState.NEW, suspended = true))
        assertTrue(WordSelector.buildQueue(due, news, remainingNewTarget = 5).isEmpty())
    }

    @Test
    fun `剩余目标为0则不取新词`() {
        val news = listOf(word(1, 0, 0, LearningState.NEW))
        assertTrue(WordSelector.buildQueue(emptyList(), news, remainingNewTarget = 0).isEmpty())
    }
}
