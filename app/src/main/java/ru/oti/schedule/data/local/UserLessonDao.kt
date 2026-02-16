package ru.oti.schedule.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserLessonDao {

    @Query("SELECT * FROM user_lessons ORDER BY day, startTime")
    fun observeAll(): Flow<List<UserLessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: UserLessonEntity): Long

    @Delete
    suspend fun delete(entity: UserLessonEntity)

    @Query("DELETE FROM user_lessons")
    suspend fun clearAll()
}
