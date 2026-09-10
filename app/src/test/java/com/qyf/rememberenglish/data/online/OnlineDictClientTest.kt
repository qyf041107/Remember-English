package com.qyf.rememberenglish.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 有道 jsonapi 解析（纯 Kotlin）。样本来自 2026-09-10 真实抓包：
 * ec 已全量变为 word[] 数组结构（释义行在 trs[].tr[].l.i[]），旧 trs 顶层结构保留兼容。
 */
class OnlineDictClientTest {

    private val client = OnlineDictClient()

    @Test
    fun `parse new ec word structure (wolves)`() {
        val text = """
            {
              "ec": {
                "exam_type": ["初中", "CET4", "考研"],
                "source": {"name": "有道词典", "url": "https://dict.youdao.com"},
                "word": [
                  {
                    "usphone": "wʊlvz",
                    "ukphone": "wʊlvz",
                    "trs": [
                      {"tr": [{"l": {"i": ["n. 狼（wolf 的复数）；贪婪者；色狼"]}}]},
                      {"tr": [{"l": {"i": ["v. 狼吞虎咽（wolf 的第三人称单数）"]}}]}
                    ],
                    "return-phrase": {"l": {"i": "wolves"}}
                  }
                ]
              },
              "web_trans": {"web-translation": [{"key": "Wolves", "trans": [{"value": "狼队"}]}]}
            }
        """.trimIndent()
        val result = client.parse("wolves", text)!!
        assertEquals("wʊlvz", result.usphone)
        assertEquals(
            listOf("n. 狼（wolf 的复数）；贪婪者；色狼", "v. 狼吞虎咽（wolf 的第三人称单数）"),
            result.meanings,
        )
    }

    @Test
    fun `parse takes first entry and dedups lines`() {
        val text = """
            {
              "ec": {
                "word": [
                  {"usphone": "tɑːm", "ukphone": "tɒm", "trs": [{"tr": [{"l": {"i": ["n. 雄性动物", "汤姆（人名）", "n. 雄性动物"]}}]}]},
                  {"usphone": "x", "ukphone": "y", "trs": [{"tr": [{"l": {"i": ["另一词条"]}}]}]}
                ]
              }
            }
        """.trimIndent()
        val result = client.parse("tom", text)!!
        assertEquals(listOf("n. 雄性动物", "汤姆（人名）"), result.meanings)
        assertEquals("tɑːm", result.usphone)
    }

    @Test
    fun `parse legacy flat ec trs structure still supported`() {
        val text = """
            {
              "ec": {
                "word": "go",
                "usphone": "ɡoʊ",
                "ukphone": "ɡəʊ",
                "trs": [{"tr": {"tr": [{"line": "vi. 去；行走"}, {"line": "n. 轮到的机会"}]}}]
              }
            }
        """.trimIndent()
        val result = client.parse("go", text)!!
        assertEquals("ɡoʊ", result.usphone)
        assertEquals(listOf("vi. 去；行走", "n. 轮到的机会"), result.meanings)
    }

    @Test
    fun `parse fanyi fallback for phrases`() {
        val text = """{"fanyi":"一见钟情"}"""
        val result = client.parse("love at first sight", text)!!
        assertEquals(listOf("一见钟情"), result.meanings)
    }

    @Test
    fun `parse web_trans fallback only for same key`() {
        val text = """
            {
              "web_trans": {
                "web-translation": [
                  {"key": "wifi", "key-speech": "WIFI", "trans": [{"value": "无线网络", "support": 1125}, {"value": "网络"}]},
                  {"key": "Wifi Router", "trans": [{"value": "路由器"}]}
                ]
              }
            }
        """.trimIndent()
        val result = client.parse("wifi", text)!!
        assertEquals(listOf("无线网络", "网络"), result.meanings)
    }

    @Test
    fun `parse empty ec word array returns null`() {
        val text = """{"ec":{"exam_type":[],"word":[]}}"""
        assertNull(client.parse("xyzzy", text))
    }

    @Test
    fun `parse malformed json returns null`() {
        assertNull(client.parse("abc", "not json"))
        assertNull(client.parse("abc", ""))
    }
}
