package com.ivi.audiobook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "books",
    indices = [Index(value = ["file_path"], unique = true)],
)
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "_id")
    val id: Long = 0,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "is_internal") val isInternal: Boolean,
    val title: String,
    val author: String?,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    @ColumnInfo(name = "cover_path") val coverPath: String?,
    val hash: String?,
    @ColumnInfo(name = "added_date") val addedDate: Long,
    @ColumnInfo(name = "last_open_date") val lastOpenDate: Long = 0,
    @ColumnInfo(name = "position_ms") val positionMs: Long = 0,
    @ColumnInfo(name = "position_timestamp") val positionTimestamp: Long = 0,
    val hidden: Boolean = false,
)
