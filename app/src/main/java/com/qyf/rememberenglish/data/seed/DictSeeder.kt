package com.qyf.rememberenglish.data.seed

import android.content.Context
import androidx.room.withTransaction
import com.qyf.rememberenglish.data.db.AppDatabase
import com.qyf.rememberenglish.data.db.Mappers
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class DictFile(
    val version: Int,
    val source: String,
    val count: Int,
    val words: List<DictEntry>,
)

@Serializable
private data class DictEntry(
    val word: String,
    val usphone: String = "",
    val ukphone: String = "",
    val meanings: List<String> = emptyList(),
)

/**
 * 词库预填充：首次启动（dict_word 为空）时从 assets 导入红宝书词库；
 * 词组（source=2）单独检测导入，便于后续版本增量补充。
 * 幂等：重复调用无副作用；条目 word UNIQUE + IGNORE 冲突策略兜底。
 */
@Singleton
class DictSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded(): Int = withContext(Dispatchers.IO) {
        val dao = database.dictWordDao()
        val now = System.currentTimeMillis()
        if (dao.count() == 0) {
            val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            val file = json.decodeFromString<DictFile>(text)
            val entities = file.words.map {
                DictWordEntity(
                    word = it.word,
                    usphone = it.usphone,
                    ukphone = it.ukphone,
                    meanings = Mappers.encodeMeanings(it.meanings),
                    source = DictWordEntity.SOURCE_BUILTIN,
                    createdAt = now,
                )
            }
            database.withTransaction {
                entities.chunked(CHUNK_SIZE).forEach { dao.insertAll(it) }
            }
        }
        seedPhrasesIfNeeded(now)
        dao.count()
    }

    /** 词组/短语（source=2，来源 ECDICT MIT，用户 2026-09-08 新增）；按词组数判断是否已导入 */
    private suspend fun seedPhrasesIfNeeded(now: Long) {
        val dao = database.dictWordDao()
        if (dao.countBySource(DictWordEntity.SOURCE_PHRASE) > 0) return
        val text = context.assets.open(PHRASE_ASSET_PATH).bufferedReader().use { it.readText() }
        val phrases = json.decodeFromString<List<DictEntry>>(text)
        val entities = phrases.map {
            DictWordEntity(
                word = it.word,
                usphone = "",
                ukphone = "",
                meanings = Mappers.encodeMeanings(it.meanings),
                source = DictWordEntity.SOURCE_PHRASE,
                createdAt = now,
            )
        }
        database.withTransaction {
            entities.chunked(CHUNK_SIZE).forEach { dao.insertAll(it) }
        }
    }

    companion object {
        const val ASSET_PATH = "words_kaoyan.json"
        const val PHRASE_ASSET_PATH = "word_phrases.json"
        private const val CHUNK_SIZE = 1000
    }
}
