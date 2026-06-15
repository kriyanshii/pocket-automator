package com.pocketautomator.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recording_sessions")
data class RecordingSessionEntity(
    @PrimaryKey val id: String,
    val taskName: String,
    val packageName: String,
    val createdAt: Long
)
