package com.qyf.rememberenglish.data.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/** 在线查询结果（可入库为自定义词） */
data class OnlineWord(
    val word: String,
    val usphone: String,
    val ukphone: String,
    val meanings: List<String>,
)

/**
 * 在线查词兜底（用户 2026-09-08 批准联网）：
 * 仅当本地词库（含词组）查不到时调用有道词典开放接口（无 key，免费）。
 * 用 HttpURLConnection 而非引入网络库——只有这一处网络请求，不值得加依赖。
 */
@Singleton
class OnlineDictClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(word: String): OnlineWord? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://dict.youdao.com/jsonapi?q=${URLEncoder.encode(word, "UTF-8")}&doctype=json")
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
            parse(word.trim().lowercase(), text)
        }.getOrNull()
    }

    /** 防御性解析：ec.trs 是标准词条（音标+释义行），fanyi 是整句/词组翻译兜底 */
    internal fun parse(query: String, text: String): OnlineWord? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val ec = runCatching { root.getValue("ec").jsonObject }.getOrNull()
        val meanings = ec?.get("trs")
            ?.let { runCatching { it.jsonArray }.getOrNull() }
            ?.mapNotNull { tr ->
                runCatching {
                    tr.jsonObject.getValue("tr").jsonObject.getValue("tr").jsonArray
                        .first().jsonObject.getValue("line").jsonPrimitive.content.trim()
                }.getOrNull()
            }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        if (meanings.isNotEmpty()) {
            return OnlineWord(
                word = runCatching { ec!!.getValue("word").jsonPrimitive.content }.getOrDefault(query),
                usphone = runCatching { ec!!.getValue("usphone").jsonPrimitive.content }.getOrDefault(""),
                ukphone = runCatching { ec!!.getValue("ukphone").jsonPrimitive.content }.getOrDefault(""),
                meanings = meanings.take(6),
            )
        }
        // 词组/短语兜底：只有整段翻译
        val fanyi = runCatching { root.getValue("fanyi").jsonPrimitive.content.trim() }.getOrNull()
        if (!fanyi.isNullOrBlank()) return OnlineWord(word = query, usphone = "", ukphone = "", meanings = listOf(fanyi))
        return null
    }
}
