package ru.oti.schedule.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun App(
    viewModel: ScheduleViewModel = viewModel(),
    onThemeChange: (Boolean) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()

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
        getUiLessonFromUserEntity = viewModel::getUiLessonFromUserEntity
    )
}
