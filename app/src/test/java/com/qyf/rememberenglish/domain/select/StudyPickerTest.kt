package com.qyf.rememberenglish.domain.select

import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.srs.ScoreConstants
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 加权随机选词：不会/不清楚的词优先，已掌握 0.25 折（CLAUDE.md 第五节） */
class StudyPickerTest {

    private fun word(id: Long, wrong: Int = 0, unclear: Int = 0, score: Double = 0.0) = UserWord(
        id = id,
        wordId = id * 100,
        addedAt = 0L,
        score = score,
        wrongCount = wrong,
        unclearCount = unclear,
        lastAnsweredAt = if (score > 0.0 || wrong > 0) 1L else 0L,
        isSuspended = false,
    )

    @Test
    fun `空列表返回null`() {
        assertNull(StudyPicker.pickNext(emptyList(), Random(1)))
    }

    @Test
    fun `排除刚答过的词`() {
        val words = listOf(word(1), word(2))
        repeat(50) {
            val picked = StudyPicker.pickNext(words, Random(it), excludeId = 1L)
            assertEquals(2L, picked?.id)
        }
    }

    @Test
    fun `只剩一个词时即使被排除也返回它`() {
        val words = listOf(word(1))
        val picked = StudyPicker.pickNext(words, Random(1), excludeId = 1L)
        assertNull(picked)
    }

    @Test
    fun `挂起的词不会被抽到`() {
        val suspended = word(1).copy(isSuspended = true)
        val normal = word(2)
        repeat(20) {
            assertEquals(2L, StudyPicker.pickNext(listOf(suspended, normal), Random(it))?.id)
        }
    }

    @Test
    fun `错词出现频率显著高于普通词`() {
        val plain = word(1)
        val wrongHeavy = word(2, wrong = 9) // 权重 10 vs 1
        val words = listOf(plain, wrongHeavy)
        val counts = mutableMapOf<Long, Int>()
        val random = Random(42)
        repeat(2000) {
            val picked = StudyPicker.pickNext(words, random)!!
            counts[picked.id] = (counts[picked.id] ?: 0) + 1
        }
        // 理论比例约 10:1；宽松断言错词至少占 2/3
        val wrongPicks = counts[2L] ?: 0
        assertTrue("错词应明显更常出现：$counts", wrongPicks > 2000 * 2 / 3)
    }

    @Test
    fun `已掌握词出现频率约为四分之一`() {
        val plain = word(1)
        val mastered = word(2, score = ScoreConstants.MASTER_SCORE) // 权重 0.25 vs 1
        val words = listOf(plain, mastered)
        val counts = mutableMapOf<Long, Int>()
        val random = Random(7)
        repeat(2000) {
            val picked = StudyPicker.pickNext(words, random)!!
            counts[picked.id] = (counts[picked.id] ?: 0) + 1
        }
        // 理论比例 4:1；宽松断言掌握词占比 10%~30%
        val masteredPicks = counts[2L] ?: 0
        val ratio = masteredPicks.toDouble() / 2000
        assertTrue("已掌握词占比应约 0.2：$ratio", ratio in 0.10..0.30)
    }

    @Test
    fun `同一随机序列下结果可复现`() {
        val words = listOf(word(1), word(2), word(3, wrong = 1))
        val a = List(10) { StudyPicker.pickNext(words, Random(99))?.id }
        val b = List(10) { StudyPicker.pickNext(words, Random(99))?.id }
        assertEquals(a, b)
        // 乱序：并非永远取第一个
        assertNotEquals(List(10) { 1L }, a)
    }
}
