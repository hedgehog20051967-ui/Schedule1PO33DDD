package ru.oti.schedule.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ru.oti.schedule.data.ScheduleRepository
import ru.oti.schedule.model.Lesson
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle as JavaTextStyle
import java.util.*

class ScheduleWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = ScheduleRepository(context)
        val schedule = try { repository.loadSchedule() } catch (e: Exception) { null }
        val today = LocalDate.now().dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale("ru")).uppercase()
        val lessons = schedule?.lessons?.filter { it.day == today } ?: emptyList()

        provideContent {
            GlanceTheme {
                WidgetContent(today, lessons)
            }
        }
    }

    @Composable
    private fun WidgetContent(day: String, lessons: List<Lesson>) {
        val size = LocalSize.current
        val isTiny = size.width < 150.dp || size.height < 120.dp

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .padding(8.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTiny) day.take(3) else day,
                    style = TextStyle(
                        fontWeight = FontWeight.Bold, 
                        fontSize = (if (isTiny) 13 else 16).sp,
                        color = GlanceTheme.colors.primary
                    )
                )
                if (!isTiny) {
                    Spacer(modifier = GlanceModifier.defaultWeight())
                    Text(
                        text = "Studify",
                        style = TextStyle(fontSize = 10.sp, color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }
            
            Spacer(modifier = GlanceModifier.height(if (isTiny) 2.dp else 6.dp))
            
            if (lessons.isEmpty()) {
                Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(text = "Выходной", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant))
                }
            } else {
                val displayLessons = if (isTiny) lessons.take(2) else lessons
                displayLessons.forEach { lesson ->
                    LessonRow(lesson, isTiny)
                }
                if (isTiny && lessons.size > 2) {
                    Text(text = "...", style = TextStyle(fontSize = 10.sp))
                }
            }
        }
    }

    @Composable
    private fun LessonRow(lesson: Lesson, isTiny: Boolean) {
        Column(modifier = GlanceModifier.padding(vertical = if (isTiny) 2.dp else 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(if (isTiny) 2.dp else 3.dp)
                        .background(GlanceTheme.colors.primary)
                ) {}
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = lesson.subject,
                    style = TextStyle(
                        fontWeight = FontWeight.Medium, 
                        fontSize = (if (isTiny) 11 else 13).sp,
                        color = GlanceTheme.colors.onSurface
                    ),
                    maxLines = 1
                )
            }
            Text(
                text = lesson.time,
                style = TextStyle(
                    fontSize = (if (isTiny) 9 else 11).sp,
                    color = GlanceTheme.colors.onSurfaceVariant
                )
            )
        }
    }
}
