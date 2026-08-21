package com.ivi.audiobook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ivi.audiobook.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query(
        """
        SELECT * FROM books WHERE hidden = 0
            AND (:query = '' OR title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%')
            AND (:source = 'ALL' OR (:source = 'INTERNAL' AND is_internal = 1) OR (:source = 'USB' AND is_internal = 0))
        ORDER BY
            CASE WHEN :sortOrder = 'TITLE' THEN title END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'AUTHOR' THEN author END COLLATE NOCASE ASC,
            CASE WHEN :sortOrder = 'RECENTLY_ADDED' THEN added_date END DESC,
            CASE WHEN :sortOrder = 'RECENTLY_PLAYED' THEN last_open_date END DESC
        """,
    )
    fun observeBooks(query: String, sortOrder: String, source: String): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE _id = :id LIMIT 1")
    suspend fun getById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE hidden = 0 AND last_open_date != 0 ORDER BY last_open_date DESC LIMIT 1")
    suspend fun getMostRecentlyOpened(): BookEntity?

    @Query("SELECT _id FROM books WHERE hidden = 0 ORDER BY title COLLATE NOCASE ASC")
    suspend fun getVisibleBookIdsOrderedByTitle(): List<Long>

    @Query("SELECT * FROM books WHERE file_path = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): BookEntity?

    @Query("SELECT * FROM books WHERE hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAllBooks(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Query("DELETE FROM books WHERE _id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE books SET position_ms = :positionMs, position_timestamp = :timestamp WHERE _id = :id")
    suspend fun updatePositionAndTimestamp(id: Long, positionMs: Long, timestamp: Long)

    @Query("UPDATE books SET position_ms = :positionMs WHERE _id = :id")
    suspend fun updatePosition(id: Long, positionMs: Long)

    @Query("UPDATE books SET last_open_date = :timestamp WHERE _id = :id")
    suspend fun updateLastOpenDate(id: Long, timestamp: Long)

    @Query("UPDATE books SET hidden = :hidden WHERE _id = :id")
    suspend fun updateHidden(id: Long, hidden: Boolean)

    @Query("UPDATE books SET file_path = :filePath, is_internal = :isInternal WHERE _id = :id")
    suspend fun updateFileLocation(id: Long, filePath: String, isInternal: Boolean)

    @Query("UPDATE books SET cover_path = :coverPath WHERE _id = :id")
    suspend fun updateCoverPath(id: Long, coverPath: String)
}
