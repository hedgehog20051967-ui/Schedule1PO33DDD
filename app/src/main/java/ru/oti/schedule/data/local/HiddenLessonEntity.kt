package ru.oti.schedule.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Храним "удалённые" (скрытые) официальные пары.
 * Мы не меняем schedule.json, просто фильтруем их при показе.
 */
@Entity(tableName = "hidden_lessons")
data class HiddenLessonEntity(
    @PrimaryKey val lessonKey: String
)
