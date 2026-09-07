package com.qyf.rememberenglish

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.data.settings.SettingsRepository
import com.qyf.rememberenglish.ui.RememberEnglishAppUi
import com.qyf.rememberenglish.ui.theme.RememberEnglishTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val newIntentListeners = mutableListOf<Consumer<Intent>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkMode by settingsRepository.darkModeFlow
                .collectAsStateWithLifecycle(initialValue = null)
            RememberEnglishTheme(darkTheme = darkMode) {
                RememberEnglishAppUi(onNewIntentListener = { listener ->
                    newIntentListeners.add(listener)
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        newIntentListeners.forEach { it.accept(intent) }
    }
}
