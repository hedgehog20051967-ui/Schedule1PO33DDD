package ru.oti.schedule

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import ru.oti.schedule.ui.App
import ru.oti.schedule.ui.theme.ScheduleTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
        
        setContent {
            var darkTheme by remember { 
                mutableStateOf(prefs.getBoolean("dark_theme", false)) 
            }
            
            ScheduleTheme(darkTheme = darkTheme) {
                Surface {
                    App(onThemeChange = { isDark ->
                        darkTheme = isDark
                        prefs.edit().putBoolean("dark_theme", isDark).apply()
                    })
                }
            }
        }
    }
}
