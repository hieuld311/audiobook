package com.ivi.audiobook.domain.repository

import com.ivi.audiobook.domain.model.Book
import com.ivi.audiobook.domain.model.LibraryQuery
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeBooks(query: LibraryQuery): Flow<List<Book>>
    suspend fun getBook(id: Long): Book?
    suspend fun getMostRecentlyOpenedBook(): Book?
    suspend fun getOrderedBookIds(): List<Long>
    suspend fun updatePlaybackProgress(id: Long, positionMs: Long, positionChanged: Boolean)
    suspend fun markOpened(id: Long)
}
