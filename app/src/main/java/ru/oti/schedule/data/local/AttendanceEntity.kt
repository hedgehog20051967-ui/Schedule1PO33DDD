package ru.oti.schedule.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance",
    indices = [Index(value = ["lessonKey", "date"], unique = true)]
)
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonKey: String,
    val date: String, // Формат YYYY-MM-DD
    val subject: String,
    val isPresent: Boolean
)
