package ru.oti.schedule.ui

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.oti.schedule.data.local.AttendanceEntity
import ru.oti.schedule.data.local.UserLessonEntity
import ru.oti.schedule.model.Lesson
import ru.oti.schedule.model.ScheduleFile
import ru.oti.schedule.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

private val dayOrder = listOf(
    "ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    schedule: ScheduleFile?,
    lessonsByDay: Map<String, List<UiLesson>>, // Используем заранее подготовленные списки
    userLessons: List<UserLessonEntity>,
    attendance: List<AttendanceEntity>,
    hiddenOfficialKeys: Set<String>,
    isOddWeek: Boolean,
    currentTime: LocalTime,
    lessonKey: (Lesson) -> String,
    onHideOfficial: (Lesson) -> Unit,
    onUnhideOfficialByKey: (String) -> Unit,
    onClearHidden: () -> Unit,
    onAdd: (day: String, start: String, end: String, subject: String, type: String?, teacher: String?, room: String?, notes: String?, dueDate: String?) -> Unit,
    onUpdate: (id: Long, day: String, start: String, end: String, subject: String, type: String?, teacher: String?, room: String?, notes: String?, dueDate: String?) -> Unit,
    onDeleteUserLesson: (UserLessonEntity) -> Unit,
    onMarkAttendance: (Lesson, Boolean) -> Unit,
    onToggleTask: (UserLessonEntity) -> Unit,
    onThemeChange: (Boolean) -> Unit,
    toLesson: (UserLessonEntity) -> Lesson,
    getUiLessonFromUserEntity: (UserLessonEntity) -> UiLesson
) {
    val allDays = dayOrder
    var currentTab by remember { mutableIntStateOf(0) }
    val todayIndex = remember { LocalDate.now().dayOfWeek.value - 1 }
    var selectedDayIndex by remember { mutableIntStateOf(if (todayIndex in 0..6) todayIndex else 0) }
    var query by remember { mutableStateOf("") }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingLesson by remember { mutableStateOf<UiLesson?>(null) }
    var quickNoteLesson by remember { mutableStateOf<UiLesson?>(null) }
    var detailLesson by remember { mutableStateOf<UiLesson?>(null) }

    val currentDate = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Studify", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currentDate, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(8.dp))
                            Surface(color = if (isOddWeek) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                Text(if (isOddWeek) "НЕЧЕТ" else "ЧЕТ", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                actions = { IconButton(onClick = { showSettingsDialog = true }) { Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Rounded.CalendarToday, null) }, label = { Text("Пары") })
                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) }, label = { Text("Задания") })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                0 -> ScheduleTab(
                    allDays = allDays, selectedDayIndex = selectedDayIndex, todayIndex = todayIndex, onDaySelected = { selectedDayIndex = it },
                    query = query, onQueryChange = { query = it }, lessonsByDay = lessonsByDay,
                    attendance = attendance, isOddWeek = isOddWeek, currentTime = currentTime,
                    onQuickNote = { quickNoteLesson = it }, onCardClick = { detailLesson = it }, onMarkAttendance = onMarkAttendance
                )
                1 -> TasksTab(
                    userLessons = userLessons, attendance = attendance,
                    onTaskClick = { detailLesson = getUiLessonFromUserEntity(it) },
                    onQuickNote = { quickNoteLesson = getUiLessonFromUserEntity(it) },
                    onToggleTask = onToggleTask
                )
            }
        }
    }

    // Dialogs
    if (showAddDialog) LessonEditDialog(day = allDays[selectedDayIndex], onDismiss = { showAddDialog = false }, onSave = { s, e, sub, t, tea, r, n, d -> onAdd(allDays[selectedDayIndex], s, e, sub, t, tea, r, n, d); showAddDialog = false })
    if (showSettingsDialog) SettingsDialog(onDismiss = { showSettingsDialog = false }, onClearData = onClearHidden, onThemeChange = onThemeChange)
    
    editingLesson?.let { ui ->
        LessonEditDialog(day = ui.lesson.day, initialLesson = ui.lesson, initialNotes = ui.userEntity?.notes ?: "", initialDueDate = ui.userEntity?.dueDate ?: "", isEditing = true, onDismiss = { editingLesson = null },
            onSave = { s, e, sub, t, tea, r, n, d -> if (ui.source == LessonSource.USER) ui.userEntity?.let { onUpdate(it.id, ui.lesson.day, s, e, sub, t, tea, r, n, d) } else { onHideOfficial(ui.lesson); onAdd(ui.lesson.day, s, e, sub, t, tea, r, n, d) }; editingLesson = null })
    }
    quickNoteLesson?.let { ui ->
        QuickNoteDialog(initialNotes = ui.userEntity?.notes ?: "", initialDueDate = ui.userEntity?.dueDate ?: "", onDismiss = { quickNoteLesson = null },
            onSave = { n, d -> val tr = ui.lesson.time.split('-', '–', '—'); val s = tr.getOrNull(0)?.trim() ?: ""; val e = tr.getOrNull(1)?.trim() ?: ""; if (ui.source == LessonSource.USER) ui.userEntity?.let { onUpdate(it.id, it.day, it.startTime, it.endTime, it.subject, it.type, it.teacher, it.room, n, d) } else { onHideOfficial(ui.lesson); onAdd(ui.lesson.day, s, e, ui.lesson.subject, ui.lesson.type, ui.lesson.teacher, ui.lesson.room, n, d) }; quickNoteLesson = null })
    }
    detailLesson?.let { ui ->
        LessonDetailSheet(uiLesson = ui, onDismiss = { detailLesson = null }, onEdit = { detailLesson = null; editingLesson = ui }, onDelete = { detailLesson = null; if (ui.source == LessonSource.USER) ui.userEntity?.let(onDeleteUserLesson) else { onHideOfficial(ui.lesson); ui.officialKey?.let { k -> scope.launch { if (snackbarHostState.showSnackbar("Пара скрыта", "Отмена") == SnackbarResult.ActionPerformed) onUnhideOfficialByKey(k) } } } })
    }
}

@Composable
private fun ScheduleTab(
    allDays: List<String>, selectedDayIndex: Int, todayIndex: Int, onDaySelected: (Int) -> Unit, query: String, onQueryChange: (String) -> Unit,
    lessonsByDay: Map<String, List<UiLesson>>, attendance: List<AttendanceEntity>,
    isOddWeek: Boolean, currentTime: LocalTime,
    onQuickNote: (UiLesson) -> Unit, onCardClick: (UiLesson) -> Unit, onMarkAttendance: (Lesson, Boolean) -> Unit
) {
    val selectedDay = allDays[selectedDayIndex]
    val todayName = allDays.getOrNull(todayIndex) ?: ""
    
    // Оптимизация: берем уже готовые списки из ViewModel
    val todayLessons = remember(lessonsByDay, todayName) {
        lessonsByDay[todayName].orEmpty()
    }
    
    val uiLessons = remember(selectedDay, query, lessonsByDay) {
        val list = lessonsByDay[selectedDay].orEmpty()
        if (query.isBlank()) list else { 
            val q = query.trim().lowercase()
            list.filter { it.lesson.subject.lowercase().contains(q) || it.lesson.teacher?.lowercase()?.contains(q) == true } 
        }
    }

    Column {
        if (selectedDayIndex == todayIndex) {
            WhatNextCard(todayLessons, currentTime)
        }
        
        ScrollableTabRow(
            selectedTabIndex = selectedDayIndex, 
            edgePadding = 16.dp, 
            containerColor = Color.Transparent, 
            divider = {}, 
            indicator = { tp -> if (selectedDayIndex < tp.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[selectedDayIndex]), color = MaterialTheme.colorScheme.primary) }
        ) {
            allDays.forEachIndexed { i, d -> 
                Tab(
                    selected = selectedDayIndex == i, 
                    onClick = { onDaySelected(i) }, 
                    text = { Text(d.lowercase().take(3).replaceFirstChar { it.uppercase() }, fontWeight = if (selectedDayIndex == i) FontWeight.Bold else FontWeight.Normal) }
                ) 
            }
        }
        
        OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Поиск...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(12.dp), singleLine = true)
        
        if (uiLessons.isEmpty()) {
            EmptyState("На этот день пар нет", Icons.AutoMirrored.Rounded.EventNote)
        } else {
            val isSelectedToday = todayIndex == selectedDayIndex
            val todayStr = remember { LocalDate.now().toString() }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                items(uiLessons, key = { it.stableKey }) { item ->
                    val isActiveWeek = true // Фильтрация теперь происходит во ViewModel
                    val att = attendance.find { it.date == todayStr && it.lessonKey == item.stableKey }
                    LessonCard(
                        uiLesson = item, 
                        isActiveWeek = isActiveWeek, 
                        isToday = isSelectedToday, 
                        currentTime = currentTime, 
                        isPresent = att?.isPresent, 
                        onClick = { onCardClick(item) }, 
                        onQuickNote = { onQuickNote(item) }, 
                        onMarkAttendance = { onMarkAttendance(item.lesson, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatNextCard(todayLessons: List<UiLesson>, currentTime: LocalTime) {
    val status by remember(todayLessons, currentTime) {
        derivedStateOf {
            val curr = todayLessons.find { it.startTime != null && it.endTime != null && !currentTime.isBefore(it.startTime) && currentTime.isBefore(it.endTime) }
            if (curr != null) {
                "Сейчас идет: ${curr.lesson.subject} (еще ${ChronoUnit.MINUTES.between(currentTime, curr.endTime)} мин)"
            } else {
                val next = todayLessons.find { it.startTime != null && currentTime.isBefore(it.startTime) }
                if (next != null) {
                    "Следующая: ${next.lesson.subject} через ${ChronoUnit.MINUTES.between(currentTime, next.startTime)} мин"
                } else if (todayLessons.isEmpty()) {
                    "Сегодня выходной!"
                } else {
                    "Все пары на сегодня закончились"
                }
            }
        }
    }
    
    Card(Modifier.padding(16.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
            Icon(Icons.Rounded.Info, null, tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(status, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LessonCard(
    uiLesson: UiLesson, 
    isActiveWeek: Boolean, 
    isToday: Boolean, 
    currentTime: LocalTime, 
    isPresent: Boolean?, 
    onClick: () -> Unit, 
    onQuickNote: () -> Unit, 
    onMarkAttendance: (Boolean) -> Unit
) {
    val isNow = remember(isToday, isActiveWeek, currentTime, uiLesson.startTime, uiLesson.endTime) { 
        isToday && isActiveWeek && uiLesson.startTime != null && uiLesson.endTime != null && !currentTime.isBefore(uiLesson.startTime) && currentTime.isBefore(uiLesson.endTime)
    }
    
    val remText = if (isNow && uiLesson.endTime != null) "Осталось ${ChronoUnit.MINUTES.between(currentTime, uiLesson.endTime)} мин" else ""
    val prog = if (isNow && uiLesson.startTime != null && uiLesson.endTime != null) {
        (ChronoUnit.MINUTES.between(uiLesson.startTime, currentTime).toFloat() / ChronoUnit.MINUTES.between(uiLesson.startTime, uiLesson.endTime).toFloat()).coerceIn(0f, 1f)
    } else 0f
    
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (isActiveWeek) 1f else 0.5f).clip(RoundedCornerShape(24.dp)).clickable(onClick = onClick), 
        shape = RoundedCornerShape(24.dp), 
        colors = CardDefaults.cardColors(containerColor = if (isNow) uiLesson.subjectColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)), 
        border = if (isNow) androidx.compose.foundation.BorderStroke(2.dp, uiLesson.subjectColor.copy(alpha = 0.5f)) else null
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            val tr = uiLesson.lesson.time.split('-', '–', '—')
            val sStr = tr.getOrNull(0)?.trim() ?: ""
            val eStr = tr.getOrNull(1)?.trim() ?: ""
            
            Column(Modifier.background(if (isNow) uiLesson.subjectColor.copy(alpha = 0.1f) else Color.Transparent).padding(16.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(sStr, fontWeight = FontWeight.Black, fontSize = 16.sp, color = if (isNow) uiLesson.subjectColor else MaterialTheme.colorScheme.onSurface)
                if (isNow) {
                    LinearProgressIndicator(progress = { prog }, modifier = Modifier.padding(vertical = 8.dp).width(30.dp).height(4.dp).clip(RoundedCornerShape(2.dp)), color = uiLesson.subjectColor, trackColor = uiLesson.subjectColor.copy(alpha = 0.2f))
                } else {
                    Box(Modifier.padding(vertical = 4.dp).width(2.dp).height(12.dp).background(MaterialTheme.colorScheme.onSurface.copy(0.1f)))
                }
                Text(eStr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            Column(Modifier.padding(16.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(uiLesson.subjectColor))
                    Spacer(Modifier.width(8.dp))
                    Text(uiLesson.lesson.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, maxLines = 1, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onQuickNote, modifier = Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary.copy(0.1f), RoundedCornerShape(8.dp))) { 
                        Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary) 
                    } 
                }
                
                if (isNow) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) { 
                        Text(remText, style = MaterialTheme.typography.labelSmall, color = uiLesson.subjectColor, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        if (isPresent == null) {
                            TextButton(onClick = { onMarkAttendance(true) }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(24.dp)) { 
                                Text("Я ЗДЕСЬ", fontSize = 10.sp, fontWeight = FontWeight.Black) 
                            } 
                        } else {
                            Icon(if (isPresent) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, Modifier.size(16.dp), tint = if (isPresent) SubjectGreen else uiLesson.subjectColor) 
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { 
                    Icon(Icons.Rounded.Person, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(uiLesson.lesson.teacher ?: "Нет данных", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Rounded.Place, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(uiLesson.lesson.room ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) 
                }
                
                if (!uiLesson.userEntity?.notes.isNullOrBlank()) {
                    Surface(Modifier.padding(top = 12.dp).fillMaxWidth(), color = uiLesson.subjectColor.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp)) { 
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) { 
                            Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(14.dp), tint = uiLesson.subjectColor)
                            Spacer(Modifier.width(8.dp))
                            Text(uiLesson.userEntity!!.notes!!, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) 
                        } 
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(onDismiss: () -> Unit, onClearData: () -> Unit, onThemeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE) }
    var darkTheme by remember { mutableStateOf(prefs.getBoolean("dark_theme", false)) }
    
    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("Настройки", fontWeight = FontWeight.Bold) }, 
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { 
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { 
                    Text("Темная тема")
                    Switch(checked = darkTheme, onCheckedChange = { darkTheme = it; onThemeChange(it) }) 
                }
                HorizontalDivider()
                TextButton(onClick = { onClearData(); onDismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { 
                    Icon(Icons.Default.DeleteForever, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Сбросить все данные") 
                } 
            } 
        }, 
        confirmButton = { Button(onClick = onDismiss) { Text("Готово") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNoteDialog(initialNotes: String, initialDueDate: String, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var notes by remember { mutableStateOf(initialNotes) }
    var dueDate by remember { mutableStateOf(initialDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    if (showDatePicker) { 
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false }, 
            confirmButton = { 
                TextButton(onClick = { 
                    datePickerState.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }
                    showDatePicker = false 
                }) { Text("ОК") } 
            }
        ) { DatePicker(state = datePickerState) } 
    }
    
    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text("Заметка / ДЗ", fontWeight = FontWeight.Bold) }, 
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { 
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Текст заметки") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(
                    value = dueDate, 
                    onValueChange = {}, 
                    readOnly = true, 
                    label = { Text("Срок сдачи (ДЗ)") }, 
                    placeholder = { Text("Выбрать дату") }, 
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }, 
                    shape = RoundedCornerShape(12.dp), 
                    enabled = false, 
                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) 
            } 
        }, 
        confirmButton = { Button(onClick = { onSave(notes, dueDate.takeIf { it.isNotBlank() }) }) { Text("Сохранить") } }, 
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonDetailSheet(uiLesson: UiLesson, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        val lesson = uiLesson.lesson
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) { 
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(uiLesson.subjectColor))
                Spacer(Modifier.width(12.dp))
                Text(lesson.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black) 
            }
            DetailItem(Icons.Rounded.Schedule, "Время", lesson.time)
            DetailItem(Icons.Rounded.Person, "Преподаватель", lesson.teacher ?: "Не указан")
            DetailItem(Icons.Rounded.Place, "Аудитория", lesson.room ?: "—")
            if (lesson.type != null) DetailItem(Icons.Rounded.Category, "Тип занятия", lesson.type)
            uiLesson.userEntity?.notes?.takeIf { it.isNotBlank() }?.let { DetailItem(Icons.AutoMirrored.Rounded.Notes, "Заметка", it) }
            uiLesson.userEntity?.dueDate?.takeIf { it.isNotBlank() }?.let { DetailItem(Icons.Rounded.Event, "Срок сдачи", it) }
            
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { 
                Button(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) { 
                    Icon(Icons.Rounded.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Изменить") 
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { 
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Удалить") 
                } 
            }
            Spacer(Modifier.height(8.dp)) 
        }
    }
}

@Composable
private fun DetailItem(icon: ImageVector, label: String, value: String) { 
    Row(verticalAlignment = Alignment.Top) { 
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column { 
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) 
        } 
    } 
}

@Composable
private fun TasksTab(userLessons: List<UserLessonEntity>, attendance: List<AttendanceEntity>, onTaskClick: (UserLessonEntity) -> Unit, onQuickNote: (UserLessonEntity) -> Unit, onToggleTask: (UserLessonEntity) -> Unit) {
    val tasks = remember(userLessons) { userLessons.filter { !it.notes.isNullOrBlank() }.sortedBy { it.dueDate ?: "9999-99-99" } }
    val activeTasks = remember(tasks) { tasks.filter { !it.isCompleted } }
    
    Column(Modifier.fillMaxSize()) {
        val totalLessons = attendance.size
        val presentLessons = attendance.count { it.isPresent }
        val attendancePercent = if (totalLessons > 0) (presentLessons.toFloat() / totalLessons.toFloat() * 100).toInt() else 0
        
        val totalTasks = tasks.size
        val completedTasks = tasks.count { it.isCompleted }
        val tasksPercent = if (totalTasks > 0) (completedTasks.toFloat() / totalTasks.toFloat() * 100).toInt() else 0
        
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { 
            StatCard(Modifier.weight(1f), "Пары", "$attendancePercent%", "$presentLessons/$totalLessons", MaterialTheme.colorScheme.primaryContainer)
            StatCard(Modifier.weight(1f), "Задания", "$tasksPercent%", "$completedTasks/$totalTasks", MaterialTheme.colorScheme.secondaryContainer) 
        }
        
        if (activeTasks.isEmpty()) {
            EmptyState("Заданий пока нет", Icons.Rounded.DoneAll)
        } else {
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                items(activeTasks, key = { it.id }) { task ->
                    val isUrgent = remember(task.dueDate) { 
                        task.dueDate?.let { 
                            try { 
                                val due = LocalDate.parse(it)
                                ChronoUnit.DAYS.between(LocalDate.now(), due) <= 2 
                            } catch (e: Exception) { false } 
                        } ?: false 
                    }
                    
                    Card(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable { onTaskClick(task) }, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = if (isUrgent) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
                            Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleTask(task) }, colors = CheckboxDefaults.colors(checkedColor = SubjectGreen))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { 
                                Text(task.subject, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(task.notes ?: "", style = MaterialTheme.typography.bodyMedium, maxLines = 10)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) { 
                                    Text("${task.day.lowercase()}, ${task.startTime}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (task.dueDate != null) { 
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Rounded.Event, null, Modifier.size(10.dp), tint = if (isUrgent && !task.isCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(4.dp))
                                        Text(task.dueDate!!, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isUrgent && !task.isCompleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) 
                                    } 
                                } 
                            }
                            IconButton(onClick = { onQuickNote(task) }) { 
                                Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) 
                            } 
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String, subValue: String, containerColor: Color) { 
    Card(modifier = modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = containerColor.copy(alpha = 0.4f))) { 
        Column(Modifier.padding(16.dp)) { 
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text(subValue, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) 
        } 
    } 
}

@Composable
private fun EmptyState(message: String, icon: ImageVector) { 
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { 
        Column(horizontalAlignment = Alignment.CenterHorizontally) { 
            Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(0.2f))
            Spacer(Modifier.height(16.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) 
        } 
    } 
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonEditDialog(day: String, initialLesson: Lesson? = null, initialNotes: String = "", initialDueDate: String = "", isEditing: Boolean = false, onDismiss: () -> Unit, onSave: (start: String, end: String, subject: String, type: String?, teacher: String?, room: String?, notes: String?, dueDate: String?) -> Unit) {
    val context = LocalContext.current
    val is24Hour = DateFormat.is24HourFormat(context)
    val tr = initialLesson?.time?.split('-', '–', '—')
    
    var subject by remember { mutableStateOf(initialLesson?.subject ?: "") }
    var start by remember { mutableStateOf(tr?.getOrNull(0)?.trim() ?: "") }
    var end by remember { mutableStateOf(tr?.getOrNull(1)?.trim() ?: "") }
    var teacher by remember { mutableStateOf(initialLesson?.teacher ?: "") }
    var room by remember { mutableStateOf(initialLesson?.room ?: "") }
    var notes by remember { mutableStateOf(initialNotes) }
    var dueDate by remember { mutableStateOf(initialDueDate) }
    
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    
    if (showStartTimePicker) { 
        val ts = rememberTimePickerState(initialHour = start.split(":").getOrNull(0)?.toIntOrNull() ?: 8, initialMinute = start.split(":").getOrNull(1)?.toIntOrNull() ?: 30, is24Hour = is24Hour)
        TimePickerDialog(onDismiss = { showStartTimePicker = false }, onConfirm = { start = String.format(Locale.getDefault(), "%02d:%02d", ts.hour, ts.minute); showStartTimePicker = false }) { TimePicker(state = ts) } 
    }
    if (showEndTimePicker) { 
        val ts = rememberTimePickerState(initialHour = end.split(":").getOrNull(0)?.toIntOrNull() ?: 10, initialMinute = end.split(":").getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = is24Hour)
        TimePickerDialog(onDismiss = { showEndTimePicker = false }, onConfirm = { end = String.format(Locale.getDefault(), "%02d:%02d", ts.hour, ts.minute); showEndTimePicker = false }) { TimePicker(state = ts) } 
    }
    if (showDatePicker) { 
        val ds = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { ds.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }; showDatePicker = false }) { Text("ОК") } }) { DatePicker(state = ds) } 
    }
    
    fun validTime(t: String): Boolean = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(t.trim())
    val canSave = subject.isNotBlank() && validTime(start) && validTime(end)
    
    AlertDialog(
        onDismissRequest = onDismiss, 
        title = { Text(if (isEditing) "Изменить пару" else "Новая пара", fontWeight = FontWeight.Bold) }, 
        text = { 
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { 
                OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Предмет") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { 
                    OutlinedTextField(value = start, onValueChange = {}, readOnly = true, label = { Text("Начало") }, modifier = Modifier.weight(1f).clickable { showStartTimePicker = true }, shape = RoundedCornerShape(12.dp), enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant))
                    OutlinedTextField(value = end, onValueChange = {}, readOnly = true, label = { Text("Конец") }, modifier = Modifier.weight(1f).clickable { showEndTimePicker = true }, shape = RoundedCornerShape(12.dp), enabled = false, colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant)) 
                }
                OutlinedTextField(value = teacher, onValueChange = { teacher = it }, label = { Text("Преподаватель") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Аудитория") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) 
            } 
        }, 
        confirmButton = { Button(onClick = { onSave(start.trim(), end.trim(), subject.trim(), null, teacher.trim(), room.trim(), notes.trim(), dueDate.trim()) }, enabled = canSave, shape = RoundedCornerShape(12.dp)) { Text("Сохранить") } }, 
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
fun TimePickerDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, content: @Composable () -> Unit) { 
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onConfirm) { Text("ОК") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }, text = { content() } ) 
}
