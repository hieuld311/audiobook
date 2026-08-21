package com.ivi.audiobook.di

import android.content.Context
import androidx.room.Room
import com.ivi.audiobook.data.local.AudioBookDatabase
import com.ivi.audiobook.data.local.dao.BookDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AudioBookDatabase =
        Room.databaseBuilder(context, AudioBookDatabase::class.java, "audiobook.db")
            // No real user data to preserve yet — wipe and recreate on any schema change instead
            // of hand-writing a Migration for every column tweak during active development.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookDao(database: AudioBookDatabase): BookDao = database.bookDao()
}
