package com.qyf.rememberenglish.domain.search

/**
 * 联网拼写建议的准确度门槛（用户 2026-09-16：要求比词库模糊匹配更严）。
 *
 * 比本地模糊严格在哪：
 * ① 只接受有道自己给出的**纠错候选**（不是全词库遍历），且最多 3 条；
 * ② 只收**单个英文单词**——滤掉 "quarantine area" / "quarantine inspection" 这类短语与含数字、含中文的条目；
 * ③ 再叠一层编辑距离 ≤ 2 的把关，挡住有道偶尔给出的远距离联想。
 *
 * 距离取 2 而非 1：最常见的打字错误是**相邻字母换位**（recieve→receive），
 * 经典 Levenshtein 对这种情形计 2（无换位操作），取 1 会把这类全滤掉。
 * 纯函数，可单测。
 */
object SpellSuggestionFilter {

    private const val MAX_DISTANCE = 2
    private const val MAX_LENGTH = 24

    /** 最多给出这么多条建议（UI 上一行一条，再多反而干扰） */
    const val MAX_RESULTS = 3

    /**
     * 过滤并取前 [MAX_RESULTS] 条，**保持传入顺序**（即有道自己的相关度排序）。
     */
    fun filter(query: String, candidates: List<String>): List<String> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return candidates
            .map { it.trim().lowercase() }
            .filter { it != q }
            .filter { isPlainWord(it) }
            .filter { FuzzyMatcher.levenshtein(q, it) <= MAX_DISTANCE }
            .distinct()
            .take(MAX_RESULTS)
    }

    /** 只认纯英文单词：2~24 个字符，允许连字符与撇号（well-known / don't） */
    private fun isPlainWord(word: String): Boolean =
        word.length in 2..MAX_LENGTH &&
            word.all { it in 'a'..'z' || it == '-' || it == '\'' }
}
