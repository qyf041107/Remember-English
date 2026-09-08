package com.qyf.rememberenglish.domain.select

import com.qyf.rememberenglish.domain.model.UserWord
import com.qyf.rememberenglish.domain.srs.ScoreScheduler
import kotlin.random.Random

/**
 * 加权随机选词（CLAUDE.md 第五节，用户 2026-09-07 定义）：
 * 从「我要背」全部词里按 [ScoreScheduler.pickWeight] 加权乱序抽词——
 * 不会/不清楚过的词出现最勤，已掌握词 0.25 折一笔带过。
 * 纯函数（随机源注入），可单测。
 */
object StudyPicker {

    /**
     * 加权随机抽一个词；[excludeId] 避免刚答过的词立刻重复出现。
     * 权重全为 0 的情形不会出现（base ≥ 1）。
     */
    fun pickNext(words: List<UserWord>, random: Random, excludeId: Long? = null): UserWord? {
        val candidates = words.filter { !it.isSuspended && it.id != excludeId }
        if (candidates.isEmpty()) return null
        val weights = candidates.map { ScoreScheduler.pickWeight(it) }
        val total = weights.sum()
        var roll = random.nextDouble() * total
        for ((index, word) in candidates.withIndex()) {
            roll -= weights[index]
            if (roll <= 0.0) return word
        }
        return candidates.last()
    }
}
