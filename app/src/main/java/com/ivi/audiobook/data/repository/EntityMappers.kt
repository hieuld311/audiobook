package com.ivi.audiobook.data.repository

import com.ivi.audiobook.data.local.entity.BookEntity
import com.ivi.audiobook.domain.model.Book

fun BookEntity.toDomain(): Book = Book(
    id = id,
    filePath = filePath,
    isInternal = isInternal,
    title = title,
    author = author,
    durationMs = durationMs,
    coverPath = coverPath,
    addedDate = addedDate,
    lastOpenDate = lastOpenDate,
    positionMs = positionMs,
)
