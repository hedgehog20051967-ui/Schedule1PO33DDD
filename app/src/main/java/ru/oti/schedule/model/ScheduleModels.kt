package ru.oti.schedule.model

import kotlinx.serialization.Serializable

@Serializable
data class ScheduleFile(
    val group: String,
    val generated_from: String,
    val lessons: List<Lesson>
)

@Serializable
data class Lesson(
    val day: String,
    val time: String,
    val subject: String,
    val type: String? = null,
    val teacher: String? = null,
    val room: String? = null,
    /**
     * "odd" - нечетная, "even" - четная, null - всегда
     */
    val weekType: String? = null
)
