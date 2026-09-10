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

/** 展平 JSON 元素里所有字符串（l.i 可能是数组，line 是单个字符串） */
private fun flattenStrings(element: kotlinx.serialization.json.JsonElement?): List<String> = when (element) {
    is kotlinx.serialization.json.JsonArray -> element.flatMap { flattenStrings(it) }
    is kotlinx.serialization.json.JsonObject -> element.values.flatMap { flattenStrings(it) }
    is kotlinx.serialization.json.JsonPrimitive -> listOf(element.content)
    null -> emptyList()
}

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

    /**
     * 防御性解析（用户 2026-09-10 实测：ec 结构已全量变为 word[] 数组，旧 trs 顶层结构保留兼容）：
     * ① ec.word[k].trs[].tr[].l.i[]（新）→ ② ec.trs[].tr.tr[].line（旧）→ ③ fanyi 整段翻译 → ④ web_trans 同 key 网络释义
     */
    internal fun parse(query: String, text: String): OnlineWord? {
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val ec = runCatching { root.getValue("ec").jsonObject }.getOrNull()

        if (ec != null) {
            // 新结构：ec.word[]（可能多词条，取第一条）；释义行在 trs[].tr[].l.i[]（或旧式 line）
            val entry = runCatching { ec.getValue("word").jsonArray.first().jsonObject }.getOrNull()
            val meanings = entry?.get("trs")
                ?.let { runCatching { it.jsonArray }.getOrNull() }
                .orEmpty()
                .flatMap { tr ->
                    runCatching {
                        flattenStrings(tr.jsonObject.getValue("tr"))
                    }.getOrDefault(emptyList())
                }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (meanings.isNotEmpty()) {
                return OnlineWord(
                    word = query,
                    usphone = runCatching { entry!!.getValue("usphone").jsonPrimitive.content }.getOrDefault(""),
                    ukphone = runCatching { entry!!.getValue("ukphone").jsonPrimitive.content }.getOrDefault(""),
                    meanings = meanings.take(6),
                )
            }
            // 兼容旧结构：ec.trs 顶层（tr.tr[].line）
            val legacy = ec.get("trs")
                ?.let { runCatching { it.jsonArray }.getOrNull() }
                .orEmpty()
                .flatMap { tr ->
                    runCatching {
                        tr.jsonObject.getValue("tr").jsonObject.getValue("tr").jsonArray
                            .mapNotNull { line ->
                                runCatching { line.jsonObject.getValue("line").jsonPrimitive.content.trim() }.getOrNull()
                            }
                    }.getOrDefault(emptyList())
                }
                .filter { it.isNotEmpty() }
            if (legacy.isNotEmpty()) {
                return OnlineWord(
                    word = query,
                    usphone = runCatching { ec.getValue("usphone").jsonPrimitive.content }.getOrDefault(""),
                    ukphone = runCatching { ec.getValue("ukphone").jsonPrimitive.content }.getOrDefault(""),
                    meanings = legacy.take(6),
                )
            }
        }
        // 词组/短语兜底：只有整段翻译
        val fanyi = runCatching { root.getValue("fanyi").jsonPrimitive.content.trim() }.getOrNull()
        if (!fanyi.isNullOrBlank()) return OnlineWord(word = query, usphone = "", ukphone = "", meanings = listOf(fanyi))
        // 最后兜底：网页释义中 key 与查询词相同的条目（避免把无关词条当主释义）
        val web = runCatching {
            root.getValue("web_trans").jsonObject.getValue("web-translation").jsonArray
                .filter { it.jsonObject["key"]?.jsonPrimitive?.content?.equals(query, ignoreCase = true) == true }
                .flatMap { same ->
                    runCatching { same.jsonObject.getValue("trans").jsonArray }
                        .getOrDefault(emptyList())
                        .mapNotNull { t ->
                            runCatching { t.jsonObject.getValue("value").jsonPrimitive.content }.getOrNull()
                        }
                }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }.getOrDefault(emptyList())
        if (web.isNotEmpty()) return OnlineWord(word = query, usphone = "", ukphone = "", meanings = web.take(3))
        return null
    }
}
