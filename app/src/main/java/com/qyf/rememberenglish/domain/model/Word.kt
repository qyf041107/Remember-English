package com.qyf.rememberenglish.domain.model

/** 词条领域模型（纯 Kotlin，供 UI 与 domain 层使用） */
data class Word(
    val id: Long,
    val word: String,
    val usphone: String,
    val ukphone: String,
    val meanings: List<String>,
    val isCustom: Boolean,
    /** 词组/短语（dict_word source=2，用户 2026-09-08 新增） */
    val isPhrase: Boolean = false,
)
