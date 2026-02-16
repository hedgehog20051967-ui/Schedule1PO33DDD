package ru.oti.schedule.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_lessons")
data class UserLessonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Один из: ПОНЕДЕЛЬНИК ... ВОСКРЕСЕНЬЕ */
    val day: String,
    /** В формате HH:MM */
    val startTime: String,
    /** В формате HH:MM */
    val endTime: String,
    val subject: String,
    val type: String? = null,
    val teacher: String? = null,
    val room: String? = null,
    val notes: String? = null,
    val dueDate: String? = null, // Формат YYYY-MM-DD
    val isCompleted: Boolean = false,
    val completedAt: String? = null // Формат YYYY-MM-DD
)
