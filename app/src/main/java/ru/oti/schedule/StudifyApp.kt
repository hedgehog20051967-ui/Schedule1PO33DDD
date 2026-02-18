package ru.oti.schedule

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.oti.schedule.data.ScheduleRepository

class StudifyApp : Application() {

    // Глобальный скоуп для приложения
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // МГНОВЕННЫЙ СТАРТ: Начинаем читать и парсить JSON сразу же,
        // пока Android еще только рисует белый экран загрузки.
        // К моменту появления UI данные уже будут в памяти.
        applicationScope.launch {
            ScheduleRepository.getInstance(applicationContext).loadSchedule()
        }
    }
}