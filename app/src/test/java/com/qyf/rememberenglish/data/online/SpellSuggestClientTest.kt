package com.qyf.rememberenglish.data.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 有道拼写建议解析（用户 2026-09-16 批准的第三处联网接口）。
 * 样本为 2026-09-16 实测原文（explain 截断保留原样，用于固化"它不能直接当释义用"这件事）。
 */
class SpellSuggestClientTest {

    private val client = SpellSuggestClient()

    @Test
    fun `解析正常返回的建议列表`() {
        val json = """
        {"result":{"msg":"success","code":200},"data":{"entries":[
          {"explain":"n. 隔离期，检疫期；隔离，检疫; v. 对（动物或人）进行检疫隔离","entry":"quarantine"},
          {"explain":"检疫; 隔离","entry":"quarantined"},
          {"explain":"隔离区：指为了防止疾病传播而设立的区域","entry":"quarantine area"}
        ],"query":"quarntine","language":"en","type":"dict"}}
        """
        val list = client.parse(json)
        assertEquals(3, list.size)
        assertEquals("quarantine", list[0].word)
        assertEquals("quarantined", list[1].word)
        // 短语也原样返回，交给 SpellSuggestionFilter 剔除（解析层不越权过滤）
        assertEquals("quarantine area", list[2].word)
    }

    @Test
    fun `explain 带截断_不能直接当释义用`() {
        val json = """
        {"result":{"msg":"success","code":200},"data":{"entries":[
          {"explain":"v. 得到，收到；遭受，经受（特定待遇）；对……作出反应；接待，招待；接收（某人为成员）；接收，收听...","entry":"receive"}
        ],"query":"recieve","language":"en","type":"dict"}}
        """
        assertTrue(client.parse(json)[0].explain.endsWith("..."))
    }

    @Test
    fun `无语义建议时返回空列表`() {
        // 实测：乱码查询返回 404 且 data 为空对象
        val json = """{"result":{"msg":"not found","code":404},"data":{}}"""
        assertTrue(client.parse(json).isEmpty())
    }

    @Test
    fun `畸形输入不抛异常`() {
        assertTrue(client.parse("").isEmpty())
        assertTrue(client.parse("not json at all").isEmpty())
        assertTrue(client.parse("""{"data":{"entries":"不是数组"}}""").isEmpty())
        assertTrue(client.parse("""{"data":{"entries":[{"entry":""}]}}""").isEmpty())
        assertTrue(client.parse("""{"data":{"entries":[{"explain":"只有释义没有词"}]}}""").isEmpty())
    }

    @Test
    fun `词条统一小写并去空白`() {
        val json = """{"data":{"entries":[{"entry":"  Quarantine  ","explain":"x"}]}}"""
        assertEquals(listOf("quarantine"), client.parse(json).map { it.word })
    }
}
