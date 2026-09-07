package com.qyf.rememberenglish.data.db

import com.qyf.rememberenglish.data.db.entity.DailyStatEntity
import com.qyf.rememberenglish.data.db.entity.DictWordEntity
import com.qyf.rememberenglish.data.db.entity.UserWordEntity
import com.qyf.rememberenglish.domain.model.DailyProgress
import com.qyf.rememberenglish.domain.model.LearningState
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
    )

    fun UserWordEntity.toUserWord(): UserWord = UserWord(
        id = id,
        wordId = wordId,
        addedAt = addedAt,
        state = LearningState.fromCode(state),
        ease = ease,
        intervalDays = intervalDays,
        reps = reps,
        lapses = lapses,
        streak = streak,
        dueAt = dueAt,
        isSuspended = isSuspended,
        isMastered = isMastered,
    )

    fun UserWord.toEntity(): UserWordEntity = UserWordEntity(
        id = id,
        wordId = wordId,
        addedAt = addedAt,
        state = state.code,
        ease = ease,
        intervalDays = intervalDays,
        reps = reps,
        lapses = lapses,
        streak = streak,
        dueAt = dueAt,
        isSuspended = isSuspended,
        isMastered = isMastered,
    )

    fun DailyStatEntity.toProgress(newTarget: Int): DailyProgress = DailyProgress(
        day = day,
        newLearned = newLearned,
        newTarget = newTarget,
        reviewsDone = reviewsDone,
        reviewsDue = reviewsDueAtDayStart,
    )
}
