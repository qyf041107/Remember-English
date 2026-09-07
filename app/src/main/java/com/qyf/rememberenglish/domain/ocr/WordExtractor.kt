package com.qyf.rememberenglish.domain.ocr

/**
 * OCR 文本 → 英文单词候选（CLAUDE.md 第五节）：
 * 正则提取 → 小写 → 去首尾撇号/连字符 → 滤单字符与含数字 → 保序去重。
 * 纯 Kotlin，可单测。
 */
object WordExtractor {

    /** 单词 token：字母开头；前后不得再贴字母/数字（排除 "abc123" 中截出的 "abc"） */
    private val TOKEN = Regex("(?<![A-Za-z])[A-Za-z][A-Za-z'-]*(?![A-Za-z0-9])")

    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 30

    fun extract(text: String): List<String> =
        TOKEN.findAll(text)
            .map { it.value.trim('\'', '-').lowercase() }
            .filter { it.length in MIN_LENGTH..MAX_LENGTH }
            .distinct()
            .toList()
}
