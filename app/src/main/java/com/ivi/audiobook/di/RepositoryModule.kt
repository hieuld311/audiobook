package com.ivi.audiobook.di

import com.ivi.audiobook.data.repository.LocalBookRepository
import com.ivi.audiobook.domain.repository.BookRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: LocalBookRepository): BookRepository
}
