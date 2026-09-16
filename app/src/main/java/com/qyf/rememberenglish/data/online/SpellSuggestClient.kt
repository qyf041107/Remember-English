package com.qyf.rememberenglish.data.online

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** 一条拼写建议（有道原文，未经准确度过滤） */
data class SpellSuggestion(
    val word: String,
    /** 有道给的释义预览，**带 "..." 截断且无音标**，仅供筛选参考，展示前须用 OnlineDictClient 回填 */
    val explain: String,
)

/**
 * 有道拼写建议（用户 2026-09-16 批准：第三处联网接口）。
 * 仅在「本地无精确匹配 → 联网精确查也失败」之后兜底，用于纠正拼错的词。
 * 文档无公开说明，实测样例固化在 SpellSuggestClientTest。
 */
@Singleton
class SpellSuggestClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun suggest(raw: String): List<SpellSuggestion> = withContext(Dispatchers.IO) {
        val q = raw.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()
        runCatching {
            val url = URL(
                "https://dict.youdao.com/suggest?num=5&ver=3.0&doctype=json&cache=false&le=en" +
                    "&q=${URLEncoder.encode(q, "UTF-8")}",
            )
            val text = (url.openConnection() as HttpURLConnection).let { conn ->
                conn.connectTimeout = 5_000
                conn.readTimeout = 5_000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    conn.disconnect()
                }
            }
            parse(text)
        }.getOrDefault(emptyList())
    }

    /**
     * 纯函数解析（可单测）。实测两种形态：
     * ① 有建议：`data.entries[]`，每项 `entry`=词、`explain`=释义
     * ② 无建议：`{"result":{"msg":"not found","code":404},"data":{}}` → 空列表
     */
    internal fun parse(text: String): List<SpellSuggestion> {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return emptyList()
        val entries = runCatching { root.getValue("data").jsonObject.getValue("entries").jsonArray }
            .getOrNull() ?: return emptyList()
        return entries.mapNotNull { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
            val word = runCatching { obj.getValue("entry").jsonPrimitive.content }.getOrNull()
                ?.trim()?.lowercase() ?: return@mapNotNull null
            val explain = runCatching { obj.getValue("explain").jsonPrimitive.content }.getOrNull().orEmpty()
            if (word.isEmpty()) null else SpellSuggestion(word, explain)
        }
    }
}
