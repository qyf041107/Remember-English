package com.qyf.rememberenglish.domain.srs

import com.qyf.rememberenglish.domain.model.AnswerRating
import com.qyf.rememberenglish.domain.model.UserWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 分数模型（CLAUDE.md 第五节）：我知道 +1｜不清楚 +0.5｜我不会 +0；满 5 分=已掌握 */
class ScoreSchedulerTest {

    private fun word(
        score: Double = 0.0,
        wrong: Int = 0,
        unclear: Int = 0,
        answered: Long = 0L,
    ) = UserWord(
        id = 1L,
        wordId = 100L,
        addedAt = 0L,
        score = score,
        wrongCount = wrong,
        unclearCount = unclear,
        lastAnsweredAt = answered,
        isSuspended = false,
    )

    @Test
    fun `我知道加1分`() {
        val after = ScoreScheduler.applyAnswer(word(), AnswerRating.KNOW, now = 10L)
        assertEquals(1.0, after.score, 1e-9)
        assertEquals(10L, after.lastAnsweredAt)
        assertEquals(0, after.wrongCount)
        assertEquals(0, after.unclearCount)
    }

    @Test
    fun `不清楚加0点5分`() {
        val after = ScoreScheduler.applyAnswer(word(), AnswerRating.UNCLEAR, now = 10L)
        assertEquals(0.5, after.score, 1e-9)
        assertEquals(1, after.unclearCount)
        assertEquals(0, after.wrongCount)
    }

    @Test
    fun `我不会加0分但计错词`() {
        val after = ScoreScheduler.applyAnswer(word(score = 2.0), AnswerRating.WRONG, now = 10L)
        assertEquals(2.0, after.score, 1e-9)
        assertEquals(1, after.wrongCount)
        assertEquals(10L, after.lastAnsweredAt)
    }

    @Test
    fun `连续五次我知道达到掌握`() {
        var w = word()
        repeat(5) { w = ScoreScheduler.applyAnswer(w, AnswerRating.KNOW, now = it.toLong()) }
        assertEquals(5.0, w.score, 1e-9)
        assertTrue(w.isMastered)
    }

    @Test
    fun `四次我不清楚不满掌握分`() {
        var w = word()
        repeat(4) { w = ScoreScheduler.applyAnswer(w, AnswerRating.UNCLEAR, now = it.toLong()) }
        assertEquals(2.0, w.score, 1e-9)
        assertFalse(w.isMastered)
    }

    @Test
    fun `新词为0分且未作答`() {
        val fresh = ScoreScheduler.newWord(wordId = 7L, now = 42L)
        assertTrue(fresh.isNew)
        assertFalse(fresh.isMastered)
        assertEquals(7L, fresh.wordId)
    }

    @Test
    fun `答错过的0分词不再是新词`() {
        val after = ScoreScheduler.applyAnswer(word(), AnswerRating.WRONG, now = 10L)
        assertFalse(after.isNew)
        assertFalse(after.isMastered)
    }

    @Test
    fun `权重_错词与模糊词更高`() {
        val plain = word()
        val wrongOnce = word(wrong = 1)
        val unclearTwice = word(unclear = 2)
        assertEquals(1.0, ScoreScheduler.pickWeight(plain), 1e-9)
        assertEquals(2.0, ScoreScheduler.pickWeight(wrongOnce), 1e-9)
        assertEquals(3.0, ScoreScheduler.pickWeight(unclearTwice), 1e-9)
    }

    @Test
    fun `权重_已掌握打0点25折`() {
        val masteredClean = word(score = 5.0)
        val masteredWithErrors = word(score = 5.0, wrong = 2, unclear = 1)
        assertEquals(0.25, ScoreScheduler.pickWeight(masteredClean), 1e-9)
        // (1+2+1)×0.25 = 1.0：错过多次的掌握词仍比干净掌握词更容易出现
        assertEquals(1.0, ScoreScheduler.pickWeight(masteredWithErrors), 1e-9)
    }

    @Test
    fun `分数到达5分即掌握_超过也保持掌握`() {
        assertTrue(ScoreScheduler.isMastered(5.0))
        assertTrue(ScoreScheduler.isMastered(7.5))
        assertFalse(ScoreScheduler.isMastered(4.5))
    }
}
