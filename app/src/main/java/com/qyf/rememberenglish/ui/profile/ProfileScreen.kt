package com.qyf.rememberenglish.ui.profile

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.data.settings.DarkMode

/** 我的：每日目标 / 每日提醒 / 深色模式 / 统计 / 关于（CLAUDE.md 屏幕清单） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 结果以系统通知开关为准，横幅实时反映 */ }

    val notificationsEnabled = remember {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        SectionTitle(stringResource(R.string.profile_target))
        TargetRow(
            target = state.settings.dailyNewTarget,
            onMinus = { viewModel.setTarget(state.settings.dailyNewTarget - 5) },
            onPlus = { viewModel.setTarget(state.settings.dailyNewTarget + 5) },
        )

        SectionTitle(stringResource(R.string.profile_reminder))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_reminder), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "%1$02d:%2$02d".format(state.settings.reminderHour, state.settings.reminderMinute),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.settings.reminderEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= 33 && !notificationsEnabled) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            viewModel.setReminderEnabled(enabled)
                        },
                    )
                }
                if (state.settings.reminderEnabled) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.profile_reminder_time), style = MaterialTheme.typography.bodyLarge)
                        }
                        TextButton(onClick = { showTimePicker = true }) {
                            Text(
                                text = "%1$02d:%2$02d".format(state.settings.reminderHour, state.settings.reminderMinute),
                            )
                        }
                    }
                }
            }
        }
        if (!notificationsEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                ContextCompat.startActivity(context, intent, null)
            }) {
                Text(stringResource(R.string.profile_notification_permission))
            }
        }

        SectionTitle(stringResource(R.string.profile_stats_title))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCard(stringResource(R.string.profile_stats_total), state.totalWords, Modifier.weight(1f))
            Spacer(modifier = Modifier.padding(start = 12.dp))
            StatCard(stringResource(R.string.profile_stats_mastered), state.masteredWords, Modifier.weight(1f))
        }

        SectionTitle(stringResource(R.string.profile_dark_mode))
        Row {
            listOf(
                DarkMode.SYSTEM to R.string.profile_dark_system,
                DarkMode.LIGHT to R.string.profile_dark_light,
                DarkMode.DARK to R.string.profile_dark_dark,
            ).forEach { (mode, res) ->
                FilterChip(
                    selected = state.settings.darkMode == mode,
                    onClick = { viewModel.setDarkMode(mode) },
                    label = { Text(stringResource(res)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        SectionTitle(stringResource(R.string.profile_about))
        Text(
            text = stringResource(R.string.profile_about_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = state.settings.reminderHour,
            initialMinute = state.settings.reminderMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setReminderTime(timeState.hour, timeState.minute)
                    showTimePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
            text = { TimePicker(state = timeState) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun TargetRow(target: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(onClick = onMinus, modifier = Modifier.padding(start = 16.dp), enabled = target > 5) {
                Text("−", style = MaterialTheme.typography.titleLarge)
            }
            Text(
                text = target.toString(),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            FilledIconButton(onClick = onPlus, modifier = Modifier.padding(end = 16.dp), enabled = target < 100) {
                Text("+", style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = value.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
