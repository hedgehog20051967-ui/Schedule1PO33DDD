package ru.oti.schedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf // <-- Добавил
import androidx.compose.runtime.remember     // <-- Добавил
import androidx.compose.runtime.setValue     // <-- Добавил
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun App(
    viewModel: ScheduleViewModel = viewModel(),
    onThemeChange: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()

    // --- ВОТ ЭТОЙ СТРОЧКИ НЕ ХВАТАЛО ---
    // Создаем состояние для хранения ID отмененных пар
    var cancelledIds by remember { mutableStateOf(emptySet<String>()) }

    ScheduleScreen(
        schedule = uiState.schedule,
        lessonsByDay = uiState.lessonsByDay,
        userLessons = uiState.userLessons,
        attendance = uiState.attendance,
        hiddenOfficialKeys = uiState.hiddenOfficialKeys,
        isOddWeek = uiState.isOddWeek,
        currentTime = currentTime,
        lessonKey = viewModel::lessonKey,
        onHideOfficial = viewModel::hideOfficialLesson,
        onUnhideOfficialByKey = viewModel::unhideOfficialLessonByKey,
        onClearHidden = viewModel::clearAllData,
        onAdd = viewModel::addUserLesson,
        onUpdate = viewModel::updateUserLesson,
        onDeleteUserLesson = viewModel::deleteUserLesson,
        onMarkAttendance = viewModel::markAttendance,
        onToggleTask = viewModel::toggleTaskCompletion,
        onThemeChange = onThemeChange,
        toLesson = viewModel::toLesson,
        getUiLessonFromUserEntity = viewModel::getUiLessonFromUserEntity,

        // Передаем созданное состояние
        cancelledLessonIds = cancelledIds,

        // Логика обновления
        onToggleCancel = { id ->
            cancelledIds = if (cancelledIds.contains(id)) {
                cancelledIds - id // Удаляем из списка (вернуть пару)
            } else {
                cancelledIds + id // Добавляем в список (отменить пару)
            }
        }
    )
}