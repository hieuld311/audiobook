package com.ivi.audiobook.data.repository

import com.ivi.audiobook.data.local.dao.BookDao
import com.ivi.audiobook.domain.model.Book
import com.ivi.audiobook.domain.model.LibraryQuery
import com.ivi.audiobook.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class LocalBookRepository @Inject constructor(
    private val bookDao: BookDao,
) : BookRepository {

    override fun observeBooks(query: LibraryQuery): Flow<List<Book>> =
        bookDao.observeBooks(
            query = query.searchText,
            sortOrder = query.sortOrder.name,
            source = query.source.name,
        ).map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBook(id: Long): Book? = bookDao.getById(id)?.toDomain()

    override suspend fun getMostRecentlyOpenedBook(): Book? = bookDao.getMostRecentlyOpened()?.toDomain()

    override suspend fun getOrderedBookIds(): List<Long> = bookDao.getVisibleBookIdsOrderedByTitle()

    override suspend fun updatePlaybackProgress(id: Long, positionMs: Long, positionChanged: Boolean) {
        if (positionChanged) {
            bookDao.updatePositionAndTimestamp(id, positionMs, System.currentTimeMillis())
        } else {
            bookDao.updatePosition(id, positionMs)
        }
    }

    override suspend fun markOpened(id: Long) {
        bookDao.updateLastOpenDate(id, System.currentTimeMillis())
    }
}
