package ru.oti.schedule.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.oti.schedule.model.ScheduleFile
import java.util.concurrent.atomic.AtomicReference

class ScheduleRepository private constructor(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    // Кэш в памяти
    private var cachedSchedule: ScheduleFile? = null

    suspend fun loadSchedule(): ScheduleFile {
        // Если предзагрузка уже выполнена, отдаем мгновенно
        cachedSchedule?.let { return it }

        return withContext(Dispatchers.IO) {
            // Двойная проверка для потокобезопасности
            cachedSchedule?.let { return@withContext it }

            val text = context.assets.open("schedule.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val schedule = json.decodeFromString(ScheduleFile.serializer(), text)
            cachedSchedule = schedule
            schedule
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ScheduleRepository? = null

        fun getInstance(context: Context): ScheduleRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScheduleRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}