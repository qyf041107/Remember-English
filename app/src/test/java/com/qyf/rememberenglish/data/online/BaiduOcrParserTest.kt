package com.qyf.rememberenglish.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 百度手写 OCR 响应解析（纯 Kotlin，云端手写识别用户 2026-09-08 要求） */
class BaiduOcrParserTest {

    @Test
    fun `parse ocr success returns trimmed non-empty lines`() {
        val text = """
            {
              "words_result": [
                {"words": "abandon "},
                {"words": ""},
                {"words": "hierarchy"}
              ],
              "words_result_num": 3,
              "log_id": 1
            }
        """.trimIndent()
        val result = BaiduOcrParser.parseOcr(text)
        assertTrue(result is BaiduOcrResult.Ok)
        assertEquals(listOf("abandon", "hierarchy"), (result as BaiduOcrResult.Ok).lines)
    }

    @Test
    fun `parse ocr auth error detected`() {
        val text = """{"error_code":110,"error_msg":"Access token invalid"}"""
        assertEquals(BaiduOcrResult.AuthError, BaiduOcrParser.parseOcr(text))
        val expired = """{"error_code":111,"error_msg":"Access token expired"}"""
        assertEquals(BaiduOcrResult.AuthError, BaiduOcrParser.parseOcr(expired))
    }

    @Test
    fun `parse ocr quota or other errors`() {
        val text = """{"error_code":17,"error_msg":"Daily limit reached"}"""
        val result = BaiduOcrParser.parseOcr(text)
        assertTrue(result is BaiduOcrResult.Failed)
        assertEquals("Daily limit reached", (result as BaiduOcrResult.Failed).message)
    }

    @Test
    fun `parse ocr malformed json`() {
        val result = BaiduOcrParser.parseOcr("not json at all")
        assertTrue(result is BaiduOcrResult.Failed)
    }

    @Test
    fun `parse token success`() {
        val text = """{"access_token":"abc.123","expires_in":2592000,"session_key":"x"}"""
        val token = BaiduOcrParser.parseToken(text)
        assertEquals("abc.123" to 2_592_000, token)
    }

    @Test
    fun `parse token error or missing field returns null`() {
        assertNull(BaiduOcrParser.parseToken("""{"error":"invalid_client"}"""))
        assertNull(BaiduOcrParser.parseToken("garbage"))
    }
}
