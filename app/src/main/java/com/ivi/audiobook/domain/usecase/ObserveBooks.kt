package com.ivi.audiobook.domain.usecase

import com.ivi.audiobook.domain.model.Book
import com.ivi.audiobook.domain.model.LibraryQuery
import com.ivi.audiobook.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveBooks @Inject constructor(
    private val repository: BookRepository,
) {
    operator fun invoke(query: LibraryQuery): Flow<List<Book>> = repository.observeBooks(query)
}
