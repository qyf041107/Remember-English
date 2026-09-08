package com.qyf.rememberenglish.domain.search

import kotlin.math.abs
import kotlin.math.min

/**
 * 词库模糊匹配排序（用户 2026-09-08 要求：输错几个字母也能搜到，最佳匹配置顶）。
 * 匹配质量分档：精确 > 前缀 > 包含 > 编辑距离 ≤ 2；同档内按真题词频降序。
 * 纯函数，可单测。
 */
object FuzzyMatcher {

    /** 编辑距离容忍度：≤3 个字母容 1，更长容 2（避免短词误配太多） */
    private fun allowedDistance(queryLength: Int): Int = if (queryLength <= 3) 1 else 2

    /** 匹配得分，越小越靠前；Int.MAX_VALUE 表示不匹配 */
    fun score(query: String, word: String, freq: Int?): Int {
        if (word == query) return -(freq ?: 0)
        if (word.startsWith(query)) return TIER_PREFIX + (word.length - query.length) * 1000 - (freq ?: 0)
        val containsIndex = word.indexOf(query)
        if (containsIndex >= 0) return TIER_CONTAINS + word.length * 1000 + containsIndex - (freq ?: 0)
        val distance = levenshtein(query, word)
        if (distance <= allowedDistance(query.length)) {
            return TIER_FUZZY + distance * 100_000 + abs(word.length - query.length) * 1000 - (freq ?: 0)
        }
        return Int.MAX_VALUE
    }

    /**
     * 全量排序取前 [limit] 条（词库约 6700 词，逐词打分开销可忽略）。
     * [freqOf] 提供真题词频（可 null），同档内词频高的排前面。
     */
    fun <T> rank(
        query: String,
        items: List<T>,
        wordOf: (T) -> String,
        freqOf: (T) -> Int? = { null },
        limit: Int = 30,
    ): List<T> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return items.asSequence()
            .map { it to score(q, wordOf(it).lowercase(), freqOf(it)) }
            .filter { it.second != Int.MAX_VALUE }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
            .toList()
    }

    /** 经典 DP 编辑距离（滚动数组） */
    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            System.arraycopy(curr, 0, prev, 0, curr.size)
        }
        return prev[b.length]
    }

    private const val TIER_PREFIX = 1_000_000
    private const val TIER_CONTAINS = 2_000_000
    private const val TIER_FUZZY = 3_000_000
}
