package ru.oti.schedule.ui

import android.content.Context
import android.os.Build.VERSION.SDK_INT
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import ru.oti.schedule.R
import ru.oti.schedule.data.local.AttendanceEntity
import ru.oti.schedule.data.local.UserLessonEntity
import ru.oti.schedule.model.Lesson
import ru.oti.schedule.model.ScheduleFile
import ru.oti.schedule.ui.theme.*
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*

private val dayOrder = listOf("ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ")

// --- ЛОГИКА ВАЙБА ---
data class VibeState(val text: String, val iconRes: Int)

fun calculateVibe(
    lessons: List<UiLesson>,
    currentTime: LocalTime,
    isWeekend: Boolean,
    cancelledIds: Set<String>
): VibeState {
    // 0. ОТСЕИВАЕМ ПАРЫ ЧУЖОЙ НЕДЕЛИ И БЕЗ ВРЕМЕНИ
    // Это критически важно для "мигающих" пар по субботам
    val activeLessons = lessons
        .filter { it.isActiveWeek } // <-- ВОТ ЭТА МАГИЧЕСКАЯ СТРОЧКА
        .filter { it.startTime != null && it.endTime != null }
        .sortedBy { it.startTime }

    // 1. Выходной (или если пар на этой неделе нет)
    if (isWeekend || activeLessons.isEmpty()) {
        return VibeState("=ДАЙТЕ МНЕ 2 ДНЯ=", R.raw.vibe_weekend)
    }

    val firstStart = activeLessons.first().startTime!!
    val lastEnd = activeLessons.last().endTime!!

    // 2. Пары кончились
    if (currentTime.isAfter(lastEnd)) {
        return VibeState("=ДОБРА И ПОЗИТИВА=", R.raw.vibe_end)
    }

    // 3. СТРУЯЧИМ (30-45 мин до первой пары)
    if (currentTime.isBefore(firstStart) && ChronoUnit.MINUTES.between(currentTime, firstStart) <= 45) {
        return VibeState("=СТРУЯЧИМ=", R.raw.vibe_struyachim)
    }

    // 4. Поиск текущей пары
    val currentLesson = activeLessons.find {
        !currentTime.isBefore(it.startTime) && currentTime.isBefore(it.endTime)
    }

    if (currentLesson != null) {
        // ПРОВЕРКА НА ОТМЕНУ
        if (cancelledIds.contains(currentLesson.stableKey)) {
            return VibeState("=ПОЗИТИВ - ЭТО ВАЖНО=", R.raw.vibe_cancelled)
        }

        val minutesLeft = ChronoUnit.MINUTES.between(currentTime, currentLesson.endTime)

        // 5. ЗАТЯГИВАЕМ ЛЯМОЧКУ (осталось < 40 мин)
        if (minutesLeft <= 40) {
            return VibeState("=ЗАТЯГИВАЕМ ЛЯМОЧКУ=", R.raw.vibe_tighten)
        }
        // 6. ТЕРПИМ
        return VibeState("=ТЕРПИМ ТЕРПИМ=", R.raw.vibe_terpim)
    }

    // 7. СКОРО / ПЕРЕМЕНА
    return VibeState("=СКОРО=", R.raw.vibe_soon)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    schedule: ScheduleFile?,
    lessonsByDay: Map<String, List<UiLesson>>,
    userLessons: List<UserLessonEntity>,
    attendance: List<AttendanceEntity>,
    hiddenOfficialKeys: Set<String>,

    // ПАРАМЕТРЫ СО ЗНАЧЕНИЯМИ ПО УМОЛЧАНИЮ (FIX ДЛЯ App.kt)
    cancelledLessonIds: Set<String> = emptySet(),
    onToggleCancel: (String) -> Unit = {},

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
    val weekDates = remember {
        val today = LocalDate.now()
        val monday = today.with(DayOfWeek.MONDAY)
        (0..6).map { i -> monday.plusDays(i.toLong()) }
    }

    val todayIndex = remember { (LocalDate.now().dayOfWeek.value - 1).coerceIn(0, 6) }
    var selectedDayIndex by remember { mutableIntStateOf(todayIndex) }
    var currentTab by remember { mutableIntStateOf(0) }
    var query by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var editingLesson by remember { mutableStateOf<UiLesson?>(null) }
    var quickNoteLesson by remember { mutableStateOf<UiLesson?>(null) }
    var detailLesson by remember { mutableStateOf<UiLesson?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val imageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    val currentVibe = remember(lessonsByDay, currentTime, cancelledLessonIds) {
        val today = LocalDate.now()
        val isWeekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
        val todayKey = dayOrder.getOrElse(today.dayOfWeek.value - 1) { "" }
        val realTodayLessons = lessonsByDay[todayKey].orEmpty()

        calculateVibe(realTodayLessons, currentTime, isWeekend, cancelledLessonIds)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, start = 20.dp, end = 20.dp, bottom = 8.dp)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Studify",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = (-1).sp
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                        ) {
                            Text(
                                text = currentVibe.text,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(currentVibe.iconRes).crossfade(true).build(),
                                imageLoader = imageLoader,
                                contentDescription = "Vibe",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val dateStr = remember { LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM, EEEE", Locale("ru"))) }
                    Text(text = dateStr, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isOddWeek) "Нечетная" else "Четная",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isOddWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (currentTab == 0) {
                FloatingActionButton(onClick = { showAddDialog = true }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(20.dp)) {
                    Icon(Icons.Default.Add, "Добавить")
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
                NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Rounded.CalendarToday, null) }, label = { Text("Пары") })
                NavigationBarItem(selected = currentTab == 1, onClick = { currentTab = 1 }, icon = { Icon(Icons.AutoMirrored.Rounded.Notes, null) }, label = { Text("Задания") })
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                0 -> ScheduleTab(
                    weekDates = weekDates, selectedDayIndex = selectedDayIndex, todayIndex = todayIndex, onDaySelected = { selectedDayIndex = it },
                    query = query, onQueryChange = { query = it }, lessonsByDay = lessonsByDay,
                    attendance = attendance, currentTime = currentTime,
                    cancelledLessonIds = cancelledLessonIds,
                    onQuickNote = { quickNoteLesson = it }, onCardClick = { detailLesson = it },
                    onMarkAttendance = { lesson, present ->
                        onMarkAttendance(lesson, present)
                        scope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(if (present) "Посещение отмечено" else "Отметка снята")
                        }
                    }
                )
                1 -> TasksTab(userLessons = userLessons, attendance = attendance, onTaskClick = { detailLesson = getUiLessonFromUserEntity(it) }, onQuickNote = { quickNoteLesson = getUiLessonFromUserEntity(it) }, onToggleTask = onToggleTask, onDeleteTask = onDeleteUserLesson)
            }
        }
    }

    val dayNameForDialog = dayOrder[selectedDayIndex]

    if (showAddDialog) LessonEditDialog(day = dayNameForDialog, onDismiss = { showAddDialog = false }, onSave = { s, e, sub, t, tea, r, n, d -> onAdd(dayNameForDialog, s, e, sub, t, tea, r, n, d); showAddDialog = false })
    if (showSettingsDialog) SettingsDialog(onDismiss = { showSettingsDialog = false }, onClearData = onClearHidden, onThemeChange = onThemeChange)
    editingLesson?.let { ui -> LessonEditDialog(day = ui.lesson.day, initialLesson = ui.lesson, initialNotes = ui.userEntity?.notes ?: "", initialDueDate = ui.userEntity?.dueDate ?: "", isEditing = true, onDismiss = { editingLesson = null }, onSave = { s, e, sub, t, tea, r, n, d -> if (ui.source == LessonSource.USER) ui.userEntity?.let { onUpdate(it.id, ui.lesson.day, s, e, sub, t, tea, r, n, d) } else { onHideOfficial(ui.lesson); onAdd(ui.lesson.day, s, e, sub, t, tea, r, n, d) }; editingLesson = null }) }
    quickNoteLesson?.let { ui -> QuickNoteDialog(initialNotes = ui.userEntity?.notes ?: "", initialDueDate = ui.userEntity?.dueDate ?: "", onDismiss = { quickNoteLesson = null }, onSave = { n, d -> val tr = ui.lesson.time.split('-', '–', '—'); val s = tr.getOrNull(0)?.trim() ?: ""; val e = tr.getOrNull(1)?.trim() ?: ""; if (ui.source == LessonSource.USER) ui.userEntity?.let { onUpdate(it.id, it.day, it.startTime, it.endTime, it.subject, it.type, it.teacher, it.room, n, d) } else { onHideOfficial(ui.lesson); onAdd(ui.lesson.day, s, e, ui.lesson.subject, ui.lesson.type, ui.lesson.teacher, ui.lesson.room, n, d) }; quickNoteLesson = null }) }

    detailLesson?.let { ui ->
        val isCancelled = cancelledLessonIds.contains(ui.stableKey)
        LessonDetailSheet(
            uiLesson = ui,
            isCancelled = isCancelled,
            onDismiss = { detailLesson = null },
            onEdit = { detailLesson = null; editingLesson = ui },
            onDelete = { detailLesson = null; if (ui.source == LessonSource.USER) ui.userEntity?.let(onDeleteUserLesson) else { onHideOfficial(ui.lesson); ui.officialKey?.let { k -> scope.launch { if (snackbarHostState.showSnackbar("Пара скрыта", "Отмена") == SnackbarResult.ActionPerformed) onUnhideOfficialByKey(k) } } } },
            onToggleCancel = {
                onToggleCancel(ui.stableKey)
                detailLesson = null
            }
        )
    }
}

@Composable
private fun ScheduleTab(
    weekDates: List<LocalDate>, selectedDayIndex: Int, todayIndex: Int, onDaySelected: (Int) -> Unit, query: String, onQueryChange: (String) -> Unit,
    lessonsByDay: Map<String, List<UiLesson>>, attendance: List<AttendanceEntity>,
    currentTime: LocalTime, cancelledLessonIds: Set<String>,
    onQuickNote: (UiLesson) -> Unit, onCardClick: (UiLesson) -> Unit, onMarkAttendance: (Lesson, Boolean) -> Unit
) {
    val selectedKey = dayOrder[selectedDayIndex]
    val todayKey = dayOrder[todayIndex]
    val todayLessons = lessonsByDay[todayKey].orEmpty()
    val uiLessons = remember(selectedKey, query, lessonsByDay) {
        val list = lessonsByDay[selectedKey].orEmpty()
        if (query.isBlank()) list else list.filter { it.lesson.subject.lowercase().contains(query.trim().lowercase()) || it.lesson.teacher?.lowercase()?.contains(query.trim().lowercase()) == true }
    }

    Column {
        if (selectedDayIndex == todayIndex) {
            WhatNextCard(todayLessons, currentTime)
        }

        ScrollableTabRow(
            selectedTabIndex = selectedDayIndex, edgePadding = 16.dp, containerColor = Color.Transparent, divider = {},
            indicator = { tp -> if (selectedDayIndex < tp.size) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tp[selectedDayIndex]), height = 3.dp, color = MaterialTheme.colorScheme.primary) }
        ) {
            weekDates.forEachIndexed { i, date ->
                val isSelected = selectedDayIndex == i
                Tab(selected = isSelected, onClick = { onDaySelected(i) }, text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru")).uppercase(), fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium, fontSize = 13.sp)
                        Text(date.format(DateTimeFormatter.ofPattern("dd.MM")), style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f))
                    }
                })
            }
        }

        if (uiLessons.isEmpty()) {
            EmptyState(if (query.isNotEmpty()) "Ничего не найдено" else "Пар нет", Icons.Rounded.EventNote)
        } else {
            val isSelectedToday = todayIndex == selectedDayIndex
            val todayStr = remember { LocalDate.now().toString() }
            LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
                item {
                    OutlinedTextField(value = query, onValueChange = onQueryChange, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), placeholder = { Text("Поиск...") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp), singleLine = true)
                }
                items(uiLessons, key = { it.stableKey }) { item ->
                    val att = attendance.find { it.date == todayStr && it.lessonKey == item.stableKey }
                    val isCancelled = cancelledLessonIds.contains(item.stableKey)
                    LessonCard(
                        uiLesson = item, isToday = isSelectedToday, currentTime = currentTime, isPresent = att?.isPresent,
                        isCancelled = isCancelled,
                        onClick = { onCardClick(item) }, onQuickNote = { onQuickNote(item) }, onMarkAttendance = { onMarkAttendance(item.lesson, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WhatNextCard(todayLessons: List<UiLesson>, currentTime: LocalTime) {
    val (statusTitle, statusTime, color) = remember(todayLessons, currentTime) {
        val curr = todayLessons.find { it.startTime != null && it.endTime != null && !currentTime.isBefore(it.startTime) && currentTime.isBefore(it.endTime) }
        if (curr != null) Triple("Сейчас идет", curr.lesson.subject, SubjectGreen)
        else {
            val next = todayLessons.find { it.startTime != null && currentTime.isBefore(it.startTime) }
            if (next != null) {
                val mins = ChronoUnit.MINUTES.between(currentTime, next.startTime)
                val hours = mins / 60
                val timeStr = if (hours > 0) "$hours ч ${mins % 60} мин" else "$mins мин"
                Triple("Следующая через", timeStr, SubjectBlue)
            } else if (todayLessons.isEmpty()) Triple("Сегодня", "Выходной", SubjectPurple)
            else Triple("На сегодня", "Всё!", SubjectOrange)
        }
    }

    Card(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)), border = BorderStroke(1.dp, color.copy(alpha = 0.3f))) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(color, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AccessTime, null, tint = Color.White) }
            Spacer(modifier = Modifier.width(16.dp))
            Column { Text(statusTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(statusTime, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun LessonCard(uiLesson: UiLesson, isToday: Boolean, currentTime: LocalTime, isPresent: Boolean?, isCancelled: Boolean, onClick: () -> Unit, onQuickNote: () -> Unit, onMarkAttendance: (Boolean) -> Unit) {
    val isNow = !isCancelled && remember(isToday, uiLesson.isActiveWeek, currentTime, uiLesson.startTime, uiLesson.endTime) {
        isToday && uiLesson.isActiveWeek && uiLesson.startTime != null && uiLesson.endTime != null && !currentTime.isBefore(uiLesson.startTime) && currentTime.isBefore(uiLesson.endTime)
    }
    val isPast = !isCancelled && remember(isToday, currentTime, uiLesson.endTime) { isToday && uiLesson.endTime != null && currentTime.isAfter(uiLesson.endTime) }

    val cardAlpha = if (isCancelled) 0.4f else if (!uiLesson.isActiveWeek) 0.3f else if (isPast) 0.5f else 1f
    val containerColor = when {
        isCancelled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        isNow -> uiLesson.subjectColor.copy(alpha = 0.1f)
        isPast -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surface
    }

    Card(modifier = Modifier.fillMaxWidth().alpha(cardAlpha).clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = containerColor), border = if (isNow) BorderStroke(2.dp, uiLesson.subjectColor) else null) {
        Column {
            if (isNow && uiLesson.startTime != null && uiLesson.endTime != null) {
                val prog = (ChronoUnit.MINUTES.between(uiLesson.startTime, currentTime).toFloat() / ChronoUnit.MINUTES.between(uiLesson.startTime, uiLesson.endTime).toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth().height(4.dp), color = uiLesson.subjectColor, trackColor = Color.Transparent)
            }
            Row(modifier = Modifier.padding(16.dp).height(IntrinsicSize.Min)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(75.dp).padding(top = 2.dp)) {
                    if (isCancelled) {
                        Text("ОТМЕНА", fontWeight = FontWeight.Black, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    } else {
                        val tr = uiLesson.lesson.time.split('-', '–', '—')
                        Text(tr.getOrNull(0)?.trim() ?: "", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, textAlign = TextAlign.Center)
                        Text(tr.getOrNull(1)?.trim() ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.alpha(0.7f))
                    }
                }
                Box(modifier = Modifier.padding(horizontal = 12.dp).width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(if (isCancelled) MaterialTheme.colorScheme.error else uiLesson.subjectColor.copy(alpha = if (isNow) 1f else 0.4f)))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiLesson.lesson.subject,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        textDecoration = if (isCancelled) TextDecoration.LineThrough else null
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!uiLesson.lesson.room.isNullOrBlank()) {
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                                Text(uiLesson.lesson.room, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }
                        Icon(Icons.Rounded.Person, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(uiLesson.lesson.teacher ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!uiLesson.userEntity?.notes.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(12.dp), tint = uiLesson.subjectColor)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(uiLesson.userEntity!!.notes!!, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                    IconButton(onClick = onQuickNote, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Rounded.Notes, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)) }
                    if (isNow && isPresent == null && !isCancelled) {
                        FilledTonalIconButton(onClick = { onMarkAttendance(true) }, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = uiLesson.subjectColor.copy(0.2f))) { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = uiLesson.subjectColor) }
                    } else if (isPresent != null) {
                        Icon(if (isPresent) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, null, Modifier.size(24.dp), tint = if (isPresent) SubjectGreen else uiLesson.subjectColor)
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Настройки") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Темная тема"); Switch(darkTheme, { darkTheme = it; onThemeChange(it) }) }
            HorizontalDivider()
            TextButton(onClick = { onClearData(); onDismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Default.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("Сбросить данные") }
        }
    }, confirmButton = { Button(onClick = onDismiss) { Text("Готово") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickNoteDialog(initialNotes: String, initialDueDate: String, onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var notes by remember { mutableStateOf(initialNotes) }
    var dueDate by remember { mutableStateOf(initialDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    if (showDatePicker) {
        val ds = rememberDatePickerState()
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { ds.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }; showDatePicker = false }) { Text("ОК") } }) { DatePicker(ds) }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Заметка") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(notes, { notes = it }, label = { Text("Текст заметки") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            OutlinedTextField(dueDate, {}, readOnly = true, label = { Text("Срок сдачи") }, modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }, enabled = false, trailingIcon = { Icon(Icons.Rounded.Event, null) })
        }
    }, confirmButton = { Button(onClick = { onSave(notes, dueDate.takeIf { it.isNotBlank() }) }) { Text("Сохранить") } }, dismissButton = { TextButton(onDismiss) { Text("Отмена") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonDetailSheet(uiLesson: UiLesson, isCancelled: Boolean, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onToggleCancel: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding(), Arrangement.spacedBy(16.dp)) {
            Text(uiLesson.lesson.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            DetailItem(Icons.Rounded.Schedule, "Время", uiLesson.lesson.time)
            DetailItem(Icons.Rounded.Person, "Преподаватель", uiLesson.lesson.teacher ?: "Не указан")
            DetailItem(Icons.Rounded.Place, "Аудитория", uiLesson.lesson.room ?: "—")

            Button(
                onClick = onToggleCancel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = if (isCancelled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            ) {
                Icon(if (isCancelled) Icons.Rounded.Refresh else Icons.Rounded.Cancel, null)
                Spacer(Modifier.width(8.dp))
                Text(if (isCancelled) "Вернуть пару (ошибка)" else "Отменили пару")
            }

            HorizontalDivider()

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onEdit, Modifier.weight(1f)) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("Ред.") }
                OutlinedButton(onDelete, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Rounded.Delete, null); Spacer(Modifier.width(8.dp)); Text("Скрыть") }
            }
        }
    }
}

@Composable
private fun DetailItem(icon: ImageVector, label: String, value: String) {
    Row { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp)); Column { Text(label, style = MaterialTheme.typography.labelSmall); Text(value, style = MaterialTheme.typography.bodyLarge) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TasksTab(userLessons: List<UserLessonEntity>, attendance: List<AttendanceEntity>, onTaskClick: (UserLessonEntity) -> Unit, onQuickNote: (UserLessonEntity) -> Unit, onToggleTask: (UserLessonEntity) -> Unit, onDeleteTask: (UserLessonEntity) -> Unit) {
    val tasks = remember(userLessons) { userLessons.filter { !it.notes.isNullOrBlank() }.sortedBy { it.dueDate ?: "9999-99-99" } }
    if (tasks.isEmpty()) EmptyState("Заданий нет", Icons.Rounded.DoneAll)
    else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(tasks, key = { it.id }) { task ->
            Card(Modifier.fillMaxWidth().clickable { onTaskClick(task) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically // <-- ВОТ ТУТ БЫЛА ОШИБКА
                ) {
                    Checkbox(checked = task.isCompleted, onCheckedChange = { onToggleTask(task) })
                    Column(modifier = Modifier.weight(1f)) { Text(task.subject, fontWeight = FontWeight.Bold); Text(task.notes ?: "", maxLines = 2) }
                    IconButton(onClick = { onDeleteTask(task) }) { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }
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
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
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
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { ds.selectedDateMillis?.let { dueDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString() }; showDatePicker = false }) { Text("ОК") } }) { DatePicker(ds) }
    }

    fun validTime(t: String): Boolean = Regex("^([01]\\d|2[0-3]):[0-5]\\d$").matches(t.trim())
    val canSave = subject.isNotBlank() && validTime(start) && validTime(end)

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (isEditing) "Изменить пару" else "Новая пара") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(subject, { subject = it }, label = { Text("Предмет") }, modifier = Modifier.fillMaxWidth())
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(start, {}, readOnly = true, label = { Text("Начало") }, modifier = Modifier.weight(1f).clickable { showStartTimePicker = true }, enabled = false)
                OutlinedTextField(end, {}, readOnly = true, label = { Text("Конец") }, modifier = Modifier.weight(1f).clickable { showEndTimePicker = true }, enabled = false)
            }
            OutlinedTextField(teacher, { teacher = it }, label = { Text("Преподаватель") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(room, { room = it }, label = { Text("Аудитория") }, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = { Button(onClick = { onSave(start.trim(), end.trim(), subject.trim(), null, teacher.trim(), room.trim(), notes.trim(), dueDate.trim()) }, enabled = canSave) { Text("Сохранить") } }, dismissButton = { TextButton(onDismiss) { Text("Отмена") } })
}

@Composable
fun TimePickerDialog(onDismiss: () -> Unit, onConfirm: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onConfirm) { Text("ОК") } }, dismissButton = { TextButton(onDismiss) { Text("Отмена") } }, text = { content() } )
}