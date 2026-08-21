package com.ivi.audiobook.domain.usecase

import com.ivi.audiobook.domain.repository.BookRepository
import javax.inject.Inject

class SavePlaybackProgress @Inject constructor(
    private val repository: BookRepository,
) {
    suspend operator fun invoke(bookId: Long, positionMs: Long, positionChanged: Boolean) {
        repository.updatePlaybackProgress(bookId, positionMs, positionChanged)
    }
}
