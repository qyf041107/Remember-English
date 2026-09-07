package com.qyf.rememberenglish.domain.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class WordExtractorTest {

    @Test
    fun `提取普通英文句子并保序去重`() {
        val words = WordExtractor.extract("The quick brown fox jumps over the lazy dog. The dog barks!")
        assertEquals(
            listOf("the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog", "barks"),
            words,
        )
    }

    @Test
    fun `过滤含数字的词`() {
        assertEquals(listOf("word"), WordExtractor.extract("abc123 word 456"))
    }

    @Test
    fun `保留撇号与连字符词`() {
        assertEquals(listOf("don't", "stop", "well-known"), WordExtractor.extract("don't stop; well-known"))
    }

    @Test
    fun `过滤单字符`() {
        assertEquals(listOf("ab"), WordExtractor.extract("a ab"))
    }

    @Test
    fun `小写化并去重`() {
        assertEquals(listOf("apple"), WordExtractor.extract("Apple APPLE apple"))
    }

    @Test
    fun `空文本返回空表`() {
        assertEquals(emptyList<String>(), WordExtractor.extract("123 456 !!! ---"))
    }
}
