package ru.oti.schedule.ui

import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.oti.schedule.data.ScheduleRepository
import ru.oti.schedule.data.local.AppDatabase
import ru.oti.schedule.data.local.AttendanceEntity
import ru.oti.schedule.data.local.HiddenLessonEntity
import ru.oti.schedule.data.local.UserLessonEntity
import ru.oti.schedule.model.Lesson
import ru.oti.schedule.model.ScheduleFile
import ru.oti.schedule.ui.theme.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.WeekFields
import java.util.Locale

enum class LessonSource { OFFICIAL, USER }

data class UiLesson(
    val lesson: Lesson,
    val source: LessonSource,
    val userEntity: UserLessonEntity? = null,
    val stableKey: String,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val subjectColor: Color = SubjectBlue
)

val UiLesson.officialKey: String? get() = if (source == LessonSource.OFFICIAL) stableKey else null

data class DayUiState(
    val schedule: ScheduleFile? = null,
    val lessonsByDay: Map<String, List<UiLesson>> = emptyMap(),
    val userLessons: List<UserLessonEntity> = emptyList(),
    val attendance: List<AttendanceEntity> = emptyList(),
    val hiddenOfficialKeys: Set<String> = emptySet(),
    val isOddWeek: Boolean = false,
    val isLoading: Boolean = true
)

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {

    private val dayOrder = listOf("ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ")
    private val repository = ScheduleRepository(app)
    private val db = AppDatabase.get(app)
    private val userDao = db.userLessonDao()
    private val hiddenDao = db.hiddenLessonDao()
    private val attendanceDao = db.attendanceDao()
    private val prefs = app.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)

    private val _schedule = MutableStateFlow<ScheduleFile?>(null)
    private val _officialLessonsGrouped = MutableStateFlow<Map<String, List<UiLesson>>>(emptyMap())
    private val userLessonsCache = mutableMapOf<Long, UiLesson>()
    private val _isLoading = MutableStateFlow(true)
    private val _isOddWeek = MutableStateFlow(calculateIsOddWeek())
    private val _currentTime = MutableStateFlow(LocalTime.now())
    val currentTime: StateFlow<LocalTime> = _currentTime.asStateFlow()

    private val databaseFlow = combine(
        userDao.observeAll().distinctUntilChanged(),
        hiddenDao.observeKeys().map { it.toSet() }.distinctUntilChanged(),
        attendanceDao.observeAll().distinctUntilChanged()
    ) { userLessons, hiddenKeys, attendance ->
        Triple(userLessons, hiddenKeys, attendance)
    }

    val uiState: StateFlow<DayUiState> = combine(
        _schedule,
        _officialLessonsGrouped,
        databaseFlow,
        _isOddWeek,
        _isLoading
    ) { schedule, officialGrouped, dbData, isOdd, loading ->
        
        if (loading) return@combine DayUiState(isLoading = true)

        val (userLessons, hiddenKeys, attendance) = dbData
        val activeUserLessonIds = userLessons.asSequence().map { it.id }.toSet()
        userLessonsCache.keys.retainAll(activeUserLessonIds)

        val userLessonsGrouped = userLessons
            .asSequence()
            .map { getCachedUiLessonFromUserEntity(it) }
            .groupBy { it.lesson.day }
        
        val finalMap = dayOrder.associateWith { day ->
            val official = officialGrouped[day].orEmpty().filter { uiLesson ->
                val lesson = uiLesson.lesson
                val isCorrectWeek = when (lesson.weekType) {
                    "odd" -> isOdd
                    "even" -> !isOdd
                    else -> true
                }
                uiLesson.stableKey.isNotEmpty() && uiLesson.stableKey !in hiddenKeys && isCorrectWeek
            }
            val user = userLessonsGrouped[day].orEmpty()
            (official + user).sortedBy { it.startTime ?: LocalTime.MIN }
        }

        DayUiState(
            schedule = schedule,
            lessonsByDay = finalMap,
            userLessons = userLessons, 
            hiddenOfficialKeys = hiddenKeys,
            attendance = attendance,
            isOddWeek = isOdd,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUiState(isLoading = true))

    init {
        loadDataAsync()
        startClock()
    }

    private fun loadDataAsync() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loadedSchedule = repository.loadSchedule()
                val grouped = loadedSchedule.lessons.map { lesson ->
                    val (s, e) = parseLessonTime(lesson.time)
                    UiLesson(
                        lesson = lesson,
                        source = LessonSource.OFFICIAL,
                        stableKey = generateLessonKey(lesson),
                        startTime = s,
                        endTime = e,
                        subjectColor = getSubjectColor(lesson.subject)
                    )
                }.groupBy { it.lesson.day }
                
                _schedule.value = loadedSchedule
                _officialLessonsGrouped.value = grouped
                
                checkScheduleVersion(loadedSchedule)
                cleanupOldTasks()
            } catch (e: Exception) {
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun startClock() {
        viewModelScope.launch {
            while (isActive) {
                val now = LocalTime.now()
                if (now.minute != _currentTime.value.minute) {
                    _currentTime.value = now
                    _isOddWeek.value = calculateIsOddWeek()
                }
                delay(20_000)
            }
        }
    }

    private fun calculateIsOddWeek(): Boolean {
        val now = LocalDate.now()
        val weekFields = WeekFields.of(Locale.getDefault())
        return now.get(weekFields.weekOfWeekBasedYear()) % 2 != 0
    }

    private fun parseLessonTime(time: String): Pair<LocalTime?, LocalTime?> {
        return try {
            val parts = time.split('-', '–', '—')
            if (parts.size < 2) return null to null
            val start = LocalTime.parse(parts[0].trim().padStart(5, '0'))
            val end = LocalTime.parse(parts[1].trim().padStart(5, '0'))
            start to end
        } catch (e: Exception) { null to null }
    }

    private fun getSubjectColor(subject: String): Color {
        val s = subject.lowercase()
        return when {
            s.contains("программирование") || s.contains("языков") -> SubjectBlue
            s.contains("математическое") || s.contains("базы данных") -> SubjectOrange
            s.contains("экономика") || s.contains("метрология") -> SubjectGreen
            s.contains("физической") -> SubjectPink
            s.contains("технология") || s.contains("сети") -> SubjectPurple
            else -> SubjectBlue
        }
    }

    private fun generateLessonKey(lesson: Lesson): String {
        val normalizedDay = lesson.day.trim().uppercase()
        val normalizedTime = lesson.time
            .replace("–", "-")
            .replace("—", "-")
            .replace(" ", "")
            .trim()
        val normalizedSubject = lesson.subject.trim().uppercase()
        val normalizedType = lesson.type.orEmpty().trim().uppercase()
        val normalizedWeekType = lesson.weekType.orEmpty().trim().uppercase()
        return "$normalizedDay|$normalizedTime|$normalizedSubject|$normalizedType|$normalizedWeekType"
    }

    fun lessonKey(lesson: Lesson): String = generateLessonKey(lesson)

    fun markAttendance(lesson: Lesson, isPresent: Boolean) {
        viewModelScope.launch {
            attendanceDao.upsert(AttendanceEntity(lessonKey = generateLessonKey(lesson), date = LocalDate.now().toString(), subject = lesson.subject, isPresent = isPresent))
        }
    }

    fun toggleTaskCompletion(entity: UserLessonEntity) {
        viewModelScope.launch { userDao.upsert(entity.copy(isCompleted = !entity.isCompleted, completedAt = if (!entity.isCompleted) LocalDate.now().toString() else null)) }
    }

    fun addUserLesson(day: String, start: String, end: String, subject: String, type: String?, teacher: String?, room: String?, notes: String?, dueDate: String?) {
        viewModelScope.launch { userDao.upsert(UserLessonEntity(day = day.uppercase(), startTime = start.trim(), endTime = end.trim(), subject = subject.trim(), type = type, teacher = teacher, room = room, notes = notes, dueDate = dueDate)) }
    }

    fun updateUserLesson(id: Long, day: String, start: String, end: String, subject: String, type: String?, teacher: String?, room: String?, notes: String?, dueDate: String?) {
        viewModelScope.launch {
            // Пытаемся сохранить состояние выполнения при обновлении, если это возможно
            // В данном упрощенном варианте мы просто создаем новый объект, но приводим день к верхнему регистру
            userDao.upsert(UserLessonEntity(id = id, day = day.uppercase(), startTime = start.trim(), endTime = end.trim(), subject = subject.trim(), type = type, teacher = teacher, room = room, notes = notes, dueDate = dueDate)) 
        }
    }

    fun deleteUserLesson(entity: UserLessonEntity) { viewModelScope.launch { userDao.delete(entity) } }
    fun hideOfficialLesson(lesson: Lesson) { viewModelScope.launch { hiddenDao.hide(HiddenLessonEntity(lessonKey = generateLessonKey(lesson))) } }
    fun unhideOfficialLessonByKey(key: String) { viewModelScope.launch { hiddenDao.unhide(key) } }
    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            clearAllDataInternal()
        }
    }

    fun toLesson(entity: UserLessonEntity): Lesson = Lesson(day = entity.day, time = "${entity.startTime}-${entity.endTime}", subject = entity.subject, type = entity.type, teacher = entity.teacher, room = entity.room)

    fun getUiLessonFromUserEntity(entity: UserLessonEntity): UiLesson {
        val l = toLesson(entity)
        val (s, e) = parseLessonTime(l.time)
        return UiLesson(lesson = l, source = LessonSource.USER, userEntity = entity, stableKey = generateLessonKey(l), startTime = s, endTime = e, subjectColor = getSubjectColor(l.subject))
    }


    private fun getCachedUiLessonFromUserEntity(entity: UserLessonEntity): UiLesson {
        return userLessonsCache[entity.id]?.takeIf { it.userEntity == entity }
            ?: getUiLessonFromUserEntity(entity).also { userLessonsCache[entity.id] = it }
    }


    private suspend fun clearAllDataInternal() {
        hiddenDao.clearAll()
        userDao.clearAll()
        attendanceDao.clearAll()
        userLessonsCache.clear()
    }

    private fun checkScheduleVersion(currentSchedule: ScheduleFile) {
        val v = currentSchedule.generated_from
        if (prefs.getString("last_schedule_version", null) != v) {
            viewModelScope.launch(Dispatchers.IO) {
                clearAllDataInternal()
                prefs.edit().putString("last_schedule_version", v).apply()
            }
        }
    }

    private fun cleanupOldTasks() {
        val firstDayOfCurrentMonth = LocalDate.now().withDayOfMonth(1)
        viewModelScope.launch(Dispatchers.IO) {
            userDao.observeAll().first().forEach { task ->
                if (task.isCompleted && task.completedAt != null) {
                    val completedDate = runCatching { LocalDate.parse(task.completedAt) }.getOrNull()
                    if (completedDate == null || completedDate.isBefore(firstDayOfCurrentMonth)) {
                        userDao.delete(task)
                    }
                }
            }
        }
    }
}
