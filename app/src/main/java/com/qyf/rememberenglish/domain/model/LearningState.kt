package com.qyf.rememberenglish.domain.model

/** 学习状态：0 新词 / 1 学习中 / 2 复习阶段 */
enum class LearningState(val code: Int) {
    NEW(0),
    LEARNING(1),
    REVIEW(2);

    companion object {
        fun fromCode(code: Int): LearningState = entries.first { it.code == code }
    }
}

/** 三键评分：不认识 / 模糊 / 认识 */
enum class ReviewRating(val code: Int) {
    AGAIN(0),
    HARD(1),
    GOOD(2);

    companion object {
        fun fromCode(code: Int): ReviewRating = entries.first { it.code == code }
    }
}
