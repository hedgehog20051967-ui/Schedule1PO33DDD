package ru.oti.schedule.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lessonKey: String,
    val date: String, // Формат YYYY-MM-DD
    val subject: String,
    val isPresent: Boolean
)
