package com.qyf.rememberenglish.ui.mine

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.domain.srs.ScoreConstants
import com.qyf.rememberenglish.ui.components.WordRow

/**
 * 我要背：统计头 + 分数筛选 chips + 列表 + 右下 FAB 进扫词添加。
 * 点词 → 单词背诵（英文优先、三键记分）；左滑单词 → 移出（Snackbar 可撤销）。
 */
@Composable
fun MyWordsScreen(
    onWordStudyClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    viewModel: MyWordsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.removedWord) {
        state.removedWord?.let { word ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.mine_removed, word.word),
                actionLabel = context.getString(R.string.mine_undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove()
            viewModel.consumeRemoved()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        SwipeToRemoveRow(
                            wordId = word.id,
                            onRemove = viewModel::removeFromMine,
                        ) {
                            WordRow(
                                word = word,
                                inMine = true,
                                onClick = { onWordStudyClick(userWord.id) },
                                onAdd = {},
                                trailing = { ScoreLabel(userWord.score) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 左滑移出行：平时外观与普通行完全一致（前景铺不透明背景遮住底层），
 * 左滑时才在行尾露出删除标志，松手过阈值即移出（配合 Snackbar 撤销）。
 */
@Composable
private fun SwipeToRemoveRow(
    wordId: Long,
    onRemove: (Long) -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove(wordId)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.mine_remove),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    ) {
        // 前景铺与页面一致的不透明底色：未滑动时完全看不到底层的删除标志
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            content()
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

/** 行尾背分：整数不带小数点（如 3/5、4.5/5），满 5 分即已掌握 */
@Composable
private fun ScoreLabel(score: Double) {
    val scoreText = if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
    Text(
        text = stringResource(R.string.mine_score, scoreText, ScoreConstants.MASTER_SCORE.toInt()),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp),
    )
}
