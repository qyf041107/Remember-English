package com.qyf.rememberenglish.domain.model

/** 三键自评：我知道（+1 分）/ 不清楚（+0.5 分）/ 我不会（+0 分） */
enum class AnswerRating(val code: Int, val scoreGain: Double) {
    WRONG(0, 0.0),
    UNCLEAR(1, 0.5),
    KNOW(2, 1.0);

    companion object {
        fun fromCode(code: Int): AnswerRating = entries.first { it.code == code }
    }
}
