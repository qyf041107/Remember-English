package com.qyf.rememberenglish.domain.srs

import com.qyf.rememberenglish.domain.model.LearningState
import com.qyf.rememberenglish.domain.model.ReviewRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SrsSchedulerTest {

    private val now = 1_000_000_000_000L
    private val day = SrsConstants.DAY_MILLIS

    private fun reviewWord(
        intervalDays: Double = 1.0,
        ease: Double = SrsConstants.EASE_INIT,
        streak: Int = 0,
    ) = SrsScheduler.newWord(wordId = 1, now = now).copy(
        state = LearningState.REVIEW,
        intervalDays = intervalDays,
        ease = ease,
        streak = streak,
    )

    // ---- 学习态 ----

    @Test
    fun `新词不认识 间隔归零当天重现 streak清零`() {
        val next = SrsScheduler.schedule(SrsScheduler.newWord(1, now), ReviewRating.AGAIN, now)
        assertEquals(0.0, next.intervalDays, 1e-9)
        assertEquals(now, next.dueAt)
        assertEquals(LearningState.LEARNING, next.state)
        assertEquals(0, next.streak)
    }

    @Test
    fun `新词认识 间隔1天 仍在学习态`() {
        val next = SrsScheduler.schedule(SrsScheduler.newWord(1, now), ReviewRating.GOOD, now)
        assertEquals(1.0, next.intervalDays, 1e-9)
        assertEquals(now + day, next.dueAt)
        assertEquals(LearningState.LEARNING, next.state)
        assertEquals(1, next.streak)
    }

    @Test
    fun `学习态连续两次认识 毕业2天进复习态`() {
        val first = SrsScheduler.schedule(SrsScheduler.newWord(1, now), ReviewRating.GOOD, now)
        val second = SrsScheduler.schedule(first, ReviewRating.GOOD, now)
        assertEquals(2.0, second.intervalDays, 1e-9)
        assertEquals(LearningState.REVIEW, second.state)
    }

    @Test
    fun `学习态模糊 间隔1天`() {
        val next = SrsScheduler.schedule(SrsScheduler.newWord(1, now), ReviewRating.HARD, now)
        assertEquals(1.0, next.intervalDays, 1e-9)
        assertEquals(LearningState.LEARNING, next.state)
    }

    // ---- 复习态 ----

    @Test
    fun `复习认识 间隔乘ease 序列1到3`() {
        val next = SrsScheduler.schedule(reviewWord(), ReviewRating.GOOD, now)
        assertEquals(3.0, next.intervalDays, 1e-9)
        assertEquals(SrsConstants.EASE_INIT + SrsConstants.EASE_BONUS_GOOD, next.ease, 1e-9)
        assertEquals(now + (3 * day), next.dueAt)
    }

    @Test
    fun `复习模糊 间隔乘1点2且至少2天 ease下降`() {
        val next = SrsScheduler.schedule(reviewWord(intervalDays = 10.0, ease = 2.5), ReviewRating.HARD, now)
        assertEquals(12.0, next.intervalDays, 1e-9)
        assertEquals(2.5 - SrsConstants.EASE_PENALTY_HARD, next.ease, 1e-9)
    }

    @Test
    fun `复习不认识 回学习态1天 ease下降 lapses加一`() {
        val next = SrsScheduler.schedule(reviewWord(intervalDays = 30.0, ease = 2.5, streak = 5), ReviewRating.AGAIN, now)
        assertEquals(LearningState.LEARNING, next.state)
        assertEquals(1.0, next.intervalDays, 1e-9)
        assertEquals(2.5 - SrsConstants.EASE_PENALTY_LAPSE, next.ease, 1e-9)
        assertEquals(1, next.lapses)
        assertEquals(0, next.streak)
        assertFalse(next.isMastered)
    }

    @Test
    fun `ease下限1点3 上限3点0`() {
        var w = reviewWord(ease = 1.35)
        w = SrsScheduler.schedule(w, ReviewRating.AGAIN, now)
        assertEquals(SrsConstants.EASE_MIN, w.ease, 1e-9)

        var w2 = reviewWord(ease = 2.95, intervalDays = 30.0)
        w2 = SrsScheduler.schedule(w2, ReviewRating.GOOD, now)
        assertEquals(SrsConstants.EASE_MAX, w2.ease, 1e-9)
    }

    // ---- 掌握与上限 ----

    @Test
    fun `间隔达到21天判定已掌握`() {
        // 认识：ease 2.5+0.1=2.6，interval=ceil(20×2.6)=52 ≥ 21 → 已掌握
        val next = SrsScheduler.schedule(reviewWord(intervalDays = 20.0, ease = 2.5), ReviewRating.GOOD, now)
        assertEquals(52.0, next.intervalDays, 1e-9)
        assertTrue(next.isMastered)
    }

    @Test
    fun `间隔上限365天`() {
        val next = SrsScheduler.schedule(reviewWord(intervalDays = 360.0, ease = 3.0), ReviewRating.GOOD, now)
        assertEquals(SrsConstants.INTERVAL_MAX_DAYS, next.intervalDays, 1e-9)
    }
}
