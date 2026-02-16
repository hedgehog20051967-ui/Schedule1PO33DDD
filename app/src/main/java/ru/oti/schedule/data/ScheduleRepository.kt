package ru.oti.schedule.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import ru.oti.schedule.model.ScheduleFile

class ScheduleRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun loadSchedule(): ScheduleFile = cachedSchedule ?: withContext(Dispatchers.IO) {
        cachedSchedule ?: run {
            val text = context.assets.open("schedule.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            json.decodeFromString(ScheduleFile.serializer(), text).also { parsed ->
                cachedSchedule = parsed
            }
        }
    }

    companion object {
        @Volatile
        private var cachedSchedule: ScheduleFile? = null
    }
}
