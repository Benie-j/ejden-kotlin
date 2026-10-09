package com.ejden.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ejden.app.ui.intro.IntroScreen
import com.ejden.app.ui.theme.Ejden
import com.ejden.app.ui.theme.EjdenTheme

enum class Screen { Intro, Dashboard }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var screen by rememberSaveable { mutableStateOf(Screen.Intro) }

            BackHandler(enabled = screen != Screen.Intro) { screen = Screen.Intro }

            when (screen) {
                Screen.Intro -> IntroScreen(onStart = { screen = Screen.Dashboard })
                Screen.Dashboard -> EjdenTheme { DashboardPlaceholder() }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun DashboardPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().background(Ejden.colors.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Tableau de bord", color = Ejden.colors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("En construction", color = Ejden.colors.textSecondary, fontSize = 14.sp)
    }
}
