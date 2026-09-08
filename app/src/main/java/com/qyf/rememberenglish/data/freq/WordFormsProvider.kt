package com.qyf.rememberenglish.data.freq

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 单词变形映射表（assets/word_forms.json，变形词 → 原形，如 went→go、wolves→wolf）。
 * 由 tools/build-forms.mjs 生成（规则变形 + 常用不规则动词表），
 * 用于搜索复数/时态词时提示原形（用户 2026-09-08 要求）。
 */
@Singleton
class WordFormsProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cache: Map<String, String>? = null

    /** [word] 的原形；本身是原形或未知词返回 null */
    fun baseOf(word: String): String? = lazyLoad()[word.lowercase()]

    private fun lazyLoad(): Map<String, String> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val loaded = runCatching {
                val text = context.assets.open("word_forms.json").bufferedReader().use { it.readText() }
                val obj = Json.parseToJsonElement(text).jsonObject
                obj.entries.associate { (k, v) -> k to v.jsonPrimitive.content }
            }.getOrDefault(emptyMap())
            cache = loaded
            return loaded
        }
    }
}
