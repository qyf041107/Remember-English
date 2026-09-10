package com.qyf.rememberenglish.data.repository

import com.qyf.rememberenglish.data.db.Mappers
import com.qyf.rememberenglish.data.db.Mappers.toEntity
import com.qyf.rememberenglish.data.db.Mappers.toUserWord
import com.qyf.rememberenglish.data.db.Mappers.toWord
import com.qyf.rememberenglish.data.db.dao.DictWordDao
import com.qyf.rememberenglish.data.db.dao.UserWordDao
import com.qyf.rememberenglish.data.db.dao.UserWordWithWord
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import com.qyf.rememberenglish.data.freq.WordFormsProvider
import com.qyf.rememberenglish.data.freq.WordFreqProvider
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.model.Word
import com.qyf.rememberenglish.domain.search.FuzzyMatcher
import com.qyf.rememberenglish.domain.srs.ScoreScheduler
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

/** 词库搜索结果；[note] 为变形词提示（如搜 went 时显示"原形 go"） */
data class SearchHit(
    val word: Word,
    val note: String? = null,
)

@Singleton
class WordRepository @Inject constructor(
    private val dictWordDao: DictWordDao,
    private val userWordDao: UserWordDao,
    private val wordFreqProvider: WordFreqProvider,
    private val wordFormsProvider: WordFormsProvider,
) {

    /**
     * 词库搜索（模糊匹配，用户 2026-09-08 要求）：
     * 词库词 + 词组全量打分排序（精确 > 前缀 > 包含 > 编辑距离≤2，同档词频高在前）；
     * 若查询词是某词的变形（went），把原形条目置顶并注明。
     */
    suspend fun search(query: String, limit: Int = 30): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val heads = dictWordDao.getAllHeads()
        val ranked = FuzzyMatcher.rank(q, heads, { it.word }, { freqOf(it.word) }, limit)
        val entities: Map<Long, DictWordEntity> = if (ranked.isEmpty()) {
            emptyMap()
        } else {
            dictWordDao.getByIds(ranked.map { it.id }).associateBy { it.id }
        }
        val hits = ranked.mapNotNull { entities[it.id] }.map { SearchHit(it.toWord()) }
        // 搜的是变形词（went/wolves）→ 原形条目置顶展示
        val base = wordFormsProvider.baseOf(q)
        if (base != null && hits.none { it.word.word == base }) {
            dictWordDao.findByWord(base)?.let { found ->
                return listOf(SearchHit(found.toWord(), note = "“$q”的原形")) + hits
            }
        }
        return hits
    }

    fun observeWord(wordId: Long): Flow<Word?> =
        dictWordDao.observeById(wordId).map { it?.toWord() }

    suspend fun getWord(wordId: Long): Word? = dictWordDao.getById(wordId)?.toWord()

    suspend fun findByWord(word: String): Word? = dictWordDao.findByWord(word)?.toWord()

    /** 真题词频（无数据返回 null；来源见 CLAUDE.md 第八节） */
    fun freqOf(word: String): Int? = wordFreqProvider.freqOf(word)

    fun observeMyWords(): Flow<List<MyWordItem>> =
        userWordDao.observeAllWithWord().map { list -> list.map { it.toMyWordItem() } }

    suspend fun isInMine(wordId: Long): Boolean = userWordDao.findByWordId(wordId) != null

    /** 批量查已在我要背的单词（扫词页"已添加"标注，用户 2026-09-10） */
    suspend fun findMineWords(words: List<String>): Set<String> =
        if (words.isEmpty()) emptySet() else userWordDao.findMineWords(words).toSet()

    /** 按 user_word 行 id 取词卡（单词背诵页用） */
    suspend fun getUserWord(id: Long): UserWord? = userWordDao.getById(id)?.toUserWord()

    suspend fun addToMine(wordId: Long): Boolean {
        if (userWordDao.findByWordId(wordId) != null) return false
        userWordDao.insert(ScoreScheduler.newWord(wordId, System.currentTimeMillis()).toEntity())
        return true
    }

    suspend fun removeFromMine(wordId: Long) {
        userWordDao.deleteByWordId(wordId)
    }

    /** 左滑移出的撤销：按原样恢复词卡（保留分数与错记数） */
    suspend fun restoreUserWord(userWord: UserWord) {
        userWordDao.insert(userWord.toEntity())
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
