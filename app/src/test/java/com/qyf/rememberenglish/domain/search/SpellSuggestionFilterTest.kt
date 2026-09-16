package com.qyf.rememberenglish.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 联网拼写建议的准确度门槛（用户 2026-09-16：要比词库模糊匹配更严）。
 * 样本取自 2026-09-16 有道 suggest 实测返回。
 */
class SpellSuggestionFilterTest {

    @Test
    fun `相邻字母换位也能纠正`() {
        // recieve → receive 是换位错，经典 Levenshtein 计 2（无换位操作）——距离上限取 2 才能救回这类
        assertEquals(2, FuzzyMatcher.levenshtein("recieve", "receive"))
        assertTrue(SpellSuggestionFilter.filter("recieve", listOf("receive")).contains("receive"))
    }

    @Test
    fun `少打一个字母也能纠正`() {
        // quarntine → quarantine 少一个 a
        assertEquals(1, FuzzyMatcher.levenshtein("quarntine", "quarantine"))
        assertEquals(
            listOf("quarantine"),
            SpellSuggestionFilter.filter("quarntine", listOf("quarantine")),
        )
    }

    @Test
    fun `剔除短语与非纯英文词条`() {
        val raw = listOf("quarantine area", "quarantine inspection", "隔离", "covid19", "quarantine")
        assertEquals(listOf("quarantine"), SpellSuggestionFilter.filter("quarntine", raw))
    }

    @Test
    fun `与原词相同的建议不要`() {
        assertTrue(SpellSuggestionFilter.filter("quarantine", listOf("quarantine")).isEmpty())
    }

    @Test
    fun `距离过远的联想被挡掉`() {
        // cat → dog 距离 3，超出上限
        assertFalse(SpellSuggestionFilter.filter("cat", listOf("dog")).contains("dog"))
        // wifi → serendipity 更远
        assertTrue(SpellSuggestionFilter.filter("wifi", listOf("serendipity")).isEmpty())
    }

    @Test
    fun `保持有道自己的排序_距离近的也不会被提前`() {
        // 实测距离：receive=2、relieve=1、received=3、receiver=3
        // 保持传入顺序（有道相关度），不按距离重排：receive 虽然比 relieve 远，仍排在前
        assertEquals(
            listOf("receive", "relieve"),
            SpellSuggestionFilter.filter("recieve", listOf("receive", "relieve", "received", "receiver")),
        )
    }

    @Test
    fun `最多三条`() {
        // test 与这五个词距离均为 1，全部合格，只取前三条
        val raw = listOf("tests", "tent", "best", "rest", "lest")
        assertEquals(SpellSuggestionFilter.MAX_RESULTS, SpellSuggestionFilter.filter("test", raw).size)
        assertEquals(listOf("tests", "tent", "best"), SpellSuggestionFilter.filter("test", raw))
    }

    @Test
    fun `重复候选去重`() {
        assertEquals(
            listOf("quarantine"),
            SpellSuggestionFilter.filter("quarntine", listOf("quarantine", "QUARANTINE", " quarantine ")),
        )
    }

    @Test
    fun `空查询返回空`() {
        assertTrue(SpellSuggestionFilter.filter("", listOf("quarantine")).isEmpty())
        assertTrue(SpellSuggestionFilter.filter("   ", listOf("quarantine")).isEmpty())
    }

    @Test
    fun `门槛严于本地模糊_前缀档会放行的词这里被挡`() {
        // 本地模糊有"前缀"档：只看 query 是不是词的头部，不看编辑距离，故 study 能搜到 studying
        assertTrue(FuzzyMatcher.score("study", "studying", null) != Int.MAX_VALUE)
        // 联网纠错只认编辑距离 ≤2 的纠错候选（study↔studying 距离 3），不参与这种长尾扩展
        assertEquals(3, FuzzyMatcher.levenshtein("study", "studying"))
        assertTrue(SpellSuggestionFilter.filter("study", listOf("studying")).isEmpty())
    }
}
