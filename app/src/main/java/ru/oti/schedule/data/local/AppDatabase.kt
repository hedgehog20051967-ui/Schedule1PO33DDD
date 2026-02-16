package ru.oti.schedule.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [UserLessonEntity::class, HiddenLessonEntity::class, AttendanceEntity::class],
    version = 6, // Увеличили версию после добавления индекса для attendance
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userLessonDao(): UserLessonDao
    abstract fun hiddenLessonDao(): HiddenLessonDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "schedule.db"
                ).fallbackToDestructiveMigration() // Позволяет автоматически обновить базу при смене версии
                .build().also { INSTANCE = it }
            }
        }
    }
}
