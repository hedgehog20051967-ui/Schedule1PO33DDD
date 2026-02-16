package ru.oti.schedule.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HiddenLessonDao {

    @Query("SELECT lessonKey FROM hidden_lessons")
    fun observeKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun hide(entity: HiddenLessonEntity)

    @Query("DELETE FROM hidden_lessons WHERE lessonKey = :key")
    suspend fun unhide(key: String)

    @Query("DELETE FROM hidden_lessons")
    suspend fun clearAll()
}
