package com.qyf.rememberenglish.data.db

import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import com.qyf.rememberenglish.data.db.entity.UserWordEntity
import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.model.Word
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** 实体 ↔ 领域模型映射；meanings 以 JSON 数组文本存储 */
object Mappers {

    private val json = Json { ignoreUnknownKeys = true }
    private val meaningsSerializer = ListSerializer(String.serializer())

    fun encodeMeanings(meanings: List<String>): String =
        json.encodeToString(meaningsSerializer, meanings)

    fun decodeMeanings(encoded: String): List<String> =
        runCatching { json.decodeFromString(meaningsSerializer, encoded) }.getOrDefault(emptyList())

    fun DictWordEntity.toWord(): Word = Word(
        id = id,
        word = word,
        usphone = usphone,
        ukphone = ukphone,
        meanings = decodeMeanings(meanings),
        isCustom = source == DictWordEntity.SOURCE_CUSTOM,
        isPhrase = source == DictWordEntity.SOURCE_PHRASE,
    )

    fun UserWordEntity.toUserWord(): UserWord = UserWord(
        id = id,
        wordId = wordId,
        addedAt = addedAt,
        score = score,
        wrongCount = wrongCount,
        unclearCount = unclearCount,
        lastAnsweredAt = lastAnsweredAt,
        isSuspended = isSuspended,
    )

    fun UserWord.toEntity(): UserWordEntity = UserWordEntity(
        id = id,
        wordId = wordId,
        addedAt = addedAt,
        score = score,
        wrongCount = wrongCount,
        unclearCount = unclearCount,
        lastAnsweredAt = lastAnsweredAt,
        isSuspended = isSuspended,
    )
}
