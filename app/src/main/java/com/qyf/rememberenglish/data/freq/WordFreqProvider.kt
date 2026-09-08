package com.qyf.rememberenglish.data.freq

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 真题词频表（assets/word_freq.json，来源 exam-data/NETEMVocabulary，CC BY-NC-SA 4.0）。
 * 惰性一次性加载为内存 Map，OCR 候选词排序与词频展示共用。
 */
@Singleton
class WordFreqProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var cache: Map<String, Int>? = null

    fun freqOf(word: String): Int? = lazyLoad()[word.lowercase()]

    private fun lazyLoad(): Map<String, Int> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val loaded = runCatching {
                val text = context.assets.open("word_freq.json").bufferedReader().use { it.readText() }
                val obj = Json.parseToJsonElement(text).jsonObject
                obj.entries.associate { (k, v) -> k to v.jsonPrimitive.content.toInt() }
            }.getOrDefault(emptyMap())
            cache = loaded
            return loaded
        }
    }
}
