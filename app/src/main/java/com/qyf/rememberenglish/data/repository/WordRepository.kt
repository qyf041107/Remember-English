package com.qyf.rememberenglish.data.repository

import com.qyf.rememberenglish.data.db.Mappers
import com.qyf.rememberenglish.data.db.Mappers.toEntity
import com.qyf.rememberenglish.data.db.Mappers.toUserWord
import com.qyf.rememberenglish.data.db.Mappers.toWord
import com.qyf.rememberenglish.data.db.dao.DictWordDao
import com.qyf.rememberenglish.data.db.dao.UserWordDao
import com.qyf.rememberenglish.data.db.dao.UserWordWithWord
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.model.Word
import com.qyf.rememberenglish.domain.srs.SrsScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 我要背列表条目 */
data class MyWordItem(
    val word: Word,
    val userWord: UserWord,
)

/** 加词结果统计（OCR 批量加入/手动输入共用） */
data class AddWordsResult(
    val added: Int,
    val alreadyInMine: Int,
    val customCreated: Int,
)

@Singleton
class WordRepository @Inject constructor(
    private val dictWordDao: DictWordDao,
    private val userWordDao: UserWordDao,
) {

    /** 词库搜索（只搜内置红宝书词库） */
    suspend fun search(query: String, limit: Int = 100): List<Word> =
        dictWordDao.search(query.trim()).map { it.toWord() }

    fun observeWord(wordId: Long): Flow<Word?> =
        dictWordDao.observeById(wordId).map { it?.toWord() }

    suspend fun getWord(wordId: Long): Word? = dictWordDao.getById(wordId)?.toWord()

    suspend fun findByWord(word: String): Word? = dictWordDao.findByWord(word)?.toWord()

    fun observeMyWords(): Flow<List<MyWordItem>> =
        userWordDao.observeAllWithWord().map { list -> list.map { it.toMyWordItem() } }

    suspend fun isInMine(wordId: Long): Boolean = userWordDao.findByWordId(wordId) != null

    suspend fun addToMine(wordId: Long): Boolean {
        if (userWordDao.findByWordId(wordId) != null) return false
        userWordDao.insert(SrsScheduler.newWord(wordId, System.currentTimeMillis()).toEntity())
        return true
    }

    suspend fun removeFromMine(wordId: Long) {
        userWordDao.deleteByWordId(wordId)
    }

    /** 插入自定义词（OCR 未命中/手动添加），已存在则直接返回现有词条 */
    suspend fun ensureCustomWord(
        word: String,
        usphone: String = "",
        ukphone: String = "",
        meanings: List<String> = emptyList(),
    ): Word {
        val normalized = word.trim().lowercase()
        dictWordDao.findByWord(normalized)?.let { return it.toWord() }
        val entity = DictWordEntity(
            word = normalized,
            usphone = usphone,
            ukphone = ukphone,
            meanings = Mappers.encodeMeanings(meanings),
            source = DictWordEntity.SOURCE_CUSTOM,
            createdAt = System.currentTimeMillis(),
        )
        val id = dictWordDao.insert(entity)
        // IGNORE 冲突（并发插入同词）时 id 为 -1，回查已有词条
        val saved = if (id != -1L) entity.copy(id = id) else dictWordDao.findByWord(normalized)!!
        return saved.toWord()
    }

    /** 批量加词：命中词库直接加；未命中先建自定义词再加（CLAUDE.md 第五节 OCR 流程） */
    suspend fun addWords(tokens: List<String>): AddWordsResult {
        var added = 0
        var already = 0
        var custom = 0
        for (token in tokens) {
            val normalized = token.trim().lowercase()
            if (normalized.isEmpty()) continue
            val word = dictWordDao.findByWord(normalized)?.toWord()
                ?: ensureCustomWord(normalized).also { custom++ }
            if (addToMine(word.id)) added++ else already++
        }
        return AddWordsResult(added, already, custom)
    }

    private fun UserWordWithWord.toMyWordItem() = MyWordItem(
        word = word.toWord(),
        userWord = userWord.toUserWord(),
    )
}
