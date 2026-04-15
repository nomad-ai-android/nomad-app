package com.nomad.travel

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.nomad.travel.ui.chat.ChatScreen
import com.nomad.travel.ui.onboarding.LanguageScreen
import com.nomad.travel.ui.settings.SettingsScreen
import com.nomad.travel.ui.setup.ModelSetupScreen
import com.nomad.travel.ui.theme.NomadTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.Locale

private enum class Destination { LANGUAGE, SETUP, CHAT, SETTINGS }

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        val app = newBase.applicationContext as NomadApp
        val code = runBlocking { app.container.prefs.languageBlocking() } ?: "ko"
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as NomadApp
        val prefs = app.container.prefs
        val gemma = app.container.gemma

        val initial: Destination = runBlocking {
            val lang = prefs.languageBlocking()
            when {
                lang == null -> Destination.LANGUAGE
                !gemma.isActiveReady() -> Destination.SETUP
                else -> Destination.CHAT
            }
        }

        setContent {
            NomadTheme {
                var destination by remember { mutableStateOf(initial) }
                val scope = rememberCoroutineScope()

                BackHandler(enabled = destination == Destination.SETTINGS) {
                    destination = Destination.CHAT
                }

                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "nav"
                ) { dest ->
                    when (dest) {
                        Destination.LANGUAGE -> LanguageScreen(
                            onContinue = { code ->
                                scope.launch {
                                    prefs.setLanguage(code)
                                    recreate()
                                }
                            }
                        )
                        Destination.SETUP -> ModelSetupScreen(
                            onReady = { destination = Destination.CHAT }
                        )
                        Destination.CHAT -> ChatScreen(
                            onOpenSettings = { destination = Destination.SETTINGS }
                        )
                        Destination.SETTINGS -> SettingsScreen(
                            onBack = { destination = Destination.CHAT },
                            onLanguageChanged = { recreate() }
                        )
                    }
                }

                LaunchedEffect(destination) {
                    if (destination == Destination.CHAT && !gemma.isActiveReady()) {
                        destination = Destination.SETUP
                    }
                }
            }
        }
    }
}
