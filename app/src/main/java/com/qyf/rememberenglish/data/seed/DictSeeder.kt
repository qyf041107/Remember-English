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
 * 词库预填充：首次启动（dict_word 为空）时从 assets 导入红宝书词库。
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
        if (dao.count() > 0) return@withContext 0

        val text = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val file = json.decodeFromString<DictFile>(text)
        val now = System.currentTimeMillis()
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
        dao.count()
    }

    companion object {
        const val ASSET_PATH = "words_kaoyan.json"
        private const val CHUNK_SIZE = 1000
    }
}
