package com.qyf.rememberenglish.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 词库模糊匹配排序（用户 2026-09-08 要求：输错也能搜到，最佳匹配置顶） */
class FuzzyMatcherTest {

    private data class W(val word: String, val freq: Int? = null)

    @Test
    fun `levenshtein basic distances`() {
        assertEquals(0, FuzzyMatcher.levenshtein("apple", "apple"))
        assertEquals(1, FuzzyMatcher.levenshtein("apple", "appl"))
        // 相邻换位在普通 Levenshtein 中距离为 2（非 Damerau）
        assertEquals(2, FuzzyMatcher.levenshtein("apple", "appel"))
        assertEquals(3, FuzzyMatcher.levenshtein("abc", "xyz"))
    }

    @Test
    fun `exact match ranks above fuzzy candidates`() {
        val items = listOf(
            W("abandun", freq = 1), // 编辑距离 1
            W("abandx", freq = 1), // 编辑距离 2
        )
        val exact = W("abandon", freq = 10)
        val ranked = FuzzyMatcher.rank("abandon", items + exact, { it.word }, { it.freq })
        assertEquals(listOf("abandon", "abandun", "abandx"), ranked.map { it.word })
    }

    @Test
    fun `exact match beats higher-frequency prefix match`() {
        val items = listOf(W("abundance", freq = 999), W("abundant", freq = 500))
        val ranked = FuzzyMatcher.rank("abundance", items, { it.word }, { it.freq })
        assertEquals("abundance", ranked.first().word)
    }

    @Test
    fun `same tier sorted by frequency descending`() {
        val items = listOf(W("amenx", freq = 3), W("ameny", freq = 8))
        val ranked = FuzzyMatcher.rank("amen", items, { it.word }, { it.freq })
        // 两个都是前缀档且长度差相同，词频高的在前
        assertEquals("ameny", ranked.first().word)
    }

    @Test
    fun `typo within tolerance is found`() {
        val items = listOf(W("necessary"))
        assertEquals(listOf("necessary"), FuzzyMatcher.rank("necesary", items, { it.word }).map { it.word })
        assertEquals(listOf("necessary"), FuzzyMatcher.rank("necessery", items, { it.word }).map { it.word })
    }

    @Test
    fun `short word only tolerates distance 1`() {
        val items = listOf(W("cat"), W("cats"), W("catastrophe"))
        val ranked = FuzzyMatcher.rank("cqt", items, { it.word })
        // cat 距离 1 可搜到；cats 距离 2、catastrophe 距离 10 均不出现（短词容 1）
        assertEquals(listOf("cat"), ranked.map { it.word })
    }

    @Test
    fun `unrelated words are filtered out`() {
        val items = listOf(W("apple"), W("banana"), W("cherry"))
        assertTrue(FuzzyMatcher.rank("zzzz", items, { it.word }).isEmpty())
    }

    @Test
    fun `query is trimmed and lowercased`() {
        val items = listOf(W("Apple", freq = 5))
        assertEquals(listOf("Apple"), FuzzyMatcher.rank("  APP  ", items, { it.word }, { it.freq }).map { it.word })
    }

    @Test
    fun `limit caps result count`() {
        val items = (1..50).map { W("prefix$it") }
        assertEquals(30, FuzzyMatcher.rank("prefix", items, { it.word }).size)
    }

    @Test
    fun `empty query returns empty`() {
        val items = listOf(W("apple"))
        assertTrue(FuzzyMatcher.rank("  ", items, { it.word }).isEmpty())
    }
}
