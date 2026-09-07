package com.qyf.rememberenglish.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.ui.components.WordRow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 我要背：统计头 + 筛选 chips + 列表 + 右下 FAB 进扫词添加 */
@Composable
fun MyWordsScreen(
    onWordClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    viewModel: MyWordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.mine_add))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StatsHeader(state)
            LazyRow(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MineFilter.entries) { f ->
                    FilterChip(
                        selected = state.filter == f,
                        onClick = { viewModel.setFilter(f) },
                        label = {
                            Text(
                                when (f) {
                                    MineFilter.ALL -> stringResource(R.string.mine_filter_all)
                                    MineFilter.NEW -> stringResource(R.string.mine_filter_new)
                                    MineFilter.LEARNING -> stringResource(R.string.mine_filter_learning)
                                    MineFilter.MASTERED -> stringResource(R.string.mine_filter_mastered)
                                },
                            )
                        },
                    )
                }
            }

            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.mine_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn {
                    items(state.items, key = { it.first.id }) { (word, userWord) ->
                        WordRow(
                            word = word,
                            inMine = true,
                            onClick = { onWordClick(word.id) },
                            onAdd = {},
                            trailing = {
                                DueLabel(dueAt = userWord.dueAt)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(state: MyWordsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        StatItem(stringResource(R.string.mine_stat_total), state.total)
        StatItem(stringResource(R.string.mine_stat_new), state.newCount)
        StatItem(stringResource(R.string.mine_stat_learning), state.learningCount)
        StatItem(stringResource(R.string.mine_stat_mastered), state.masteredCount)
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 到期时间标签：已到期→"待复习"；今天→"今天 HH:mm"；未来→"MM-dd" */
@Composable
private fun DueLabel(dueAt: Long) {
    val now = Instant.now()
    val due = Instant.ofEpochMilli(dueAt)
    val zone = ZoneId.systemDefault()
    val text = if (dueAt <= now.toEpochMilli()) {
        stringResource(R.string.mine_due_now)
    } else {
        val dueDate = due.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        if (dueDate == today) {
            stringResource(R.string.mine_due_today, due.atZone(zone).format(todayFormatter))
        } else {
            due.atZone(zone).format(dueFormatter)
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp),
    )
}

private val dueFormatter = DateTimeFormatter.ofPattern("MM-dd")
private val todayFormatter = DateTimeFormatter.ofPattern("HH:mm")
