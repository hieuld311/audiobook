package com.ivi.audiobook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ivi.audiobook.data.local.dao.BookDao
import com.ivi.audiobook.data.local.entity.BookEntity

@Database(entities = [BookEntity::class], version = 2, exportSchema = false)
abstract class AudioBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
}
