package com.ivi.audiobook.domain.model

data class Book(
    val id: Long,
    val filePath: String,
    val isInternal: Boolean,
    val title: String,
    val author: String?,
    val durationMs: Long,
    val coverPath: String?,
    val addedDate: Long,
    val lastOpenDate: Long,
    val positionMs: Long,
) {
    val progressFraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}
