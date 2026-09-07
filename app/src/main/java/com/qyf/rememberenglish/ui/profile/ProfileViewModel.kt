package com.qyf.rememberenglish.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qyf.rememberenglish.data.db.dao.UserWordDao
import com.qyf.rememberenglish.data.settings.AppSettings
import com.qyf.rememberenglish.data.settings.SettingsRepository
import com.qyf.rememberenglish.data.worker.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val settings: AppSettings = AppSettings(),
    val totalWords: Int = 0,
    val masteredWords: Int = 0,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reminderScheduler: ReminderScheduler,
    userWordDao: UserWordDao,
) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> = combine(
        settingsRepository.settingsFlow,
        userWordDao.observeTotalCount(),
        userWordDao.observeMasteredCount(),
    ) { settings, total, mastered ->
        ProfileUiState(settings = settings, totalWords = total, masteredWords = mastered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setTarget(value: Int) {
        viewModelScope.launch { settingsRepository.setDailyNewTarget(value) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            reminderScheduler.reschedule()
        }
    }

    fun setReminderTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderTime(hour, minute)
            reminderScheduler.reschedule()
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch { settingsRepository.setDarkMode(mode) }
    }
}
