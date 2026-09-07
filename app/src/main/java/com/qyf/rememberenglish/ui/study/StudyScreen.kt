package com.qyf.rememberenglish.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.domain.model.ReviewRating

/** 今日学习：进度 + 学习会话（卡片翻转，三键评分）（CLAUDE.md 屏幕清单） */
@Composable
fun StudyScreen(
    viewModel: StudyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.study_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        state.progress?.let { ProgressCard(it) }
        Spacer(modifier = Modifier.height(16.dp))

        when (val s = state.session) {
            SessionState.Idle -> IdleContent(state, onStart = viewModel::start)
            is SessionState.Studying -> StudyingContent(s, viewModel)
            SessionState.Finished -> FinishedContent(onFinish = viewModel::quit)
        }
    }
}

@Composable
private fun ProgressCard(progress: com.qyf.rememberenglish.domain.model.DailyProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.study_progress_new,
                    progress.newLearned.coerceAtMost(progress.newTarget),
                    progress.newTarget,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (progress.newTarget == 0) 1f
                    else (progress.newLearned.toFloat() / progress.newTarget).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.study_progress_review,
                    progress.reviewsDone.coerceAtMost(progress.reviewsDue),
                    progress.reviewsDue,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (progress.reviewsDue == 0) 1f
                    else (progress.reviewsDone.toFloat() / progress.reviewsDue).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun IdleContent(state: StudyUiState, onStart: () -> Unit) {
    val progress = state.progress
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (progress != null && progress.isAllDone) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.study_done_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.study_done_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (!state.hasTodayQueue) {
                Text(
                    text = stringResource(R.string.study_idle_no_words),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Button(onClick = onStart, modifier = Modifier.size(width = 200.dp, height = 56.dp)) {
                    Text(stringResource(R.string.study_start), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun StudyingContent(s: SessionState.Studying, viewModel: StudyViewModel) {
    val word = s.word
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${s.index + 1}/${s.queue.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = viewModel::quit) {
                Text(stringResource(R.string.study_quit))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = word?.word.orEmpty(),
                    style = MaterialTheme.typography.displayMedium,
                    textAlign = TextAlign.Center,
                )
                if (!word?.usphone.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "/${word?.usphone}/",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(onClick = viewModel::speak) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.study_speak),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                if (s.revealed) {
                    if (word?.meanings.isNullOrEmpty()) {
                        Text(
                            text = stringResource(R.string.study_no_meaning),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        word!!.meanings.forEach { meaning ->
                            Text(
                                text = meaning,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                } else {
                    OutlinedButton(onClick = viewModel::reveal) {
                        Text(stringResource(R.string.study_show_meaning))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (s.revealed) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.rate(ReviewRating.AGAIN) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_again), color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick = { viewModel.rate(ReviewRating.HARD) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_hard))
                }
                Button(
                    onClick = { viewModel.rate(ReviewRating.GOOD) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_good))
                }
            }
        } else {
            Button(
                onClick = viewModel::reveal,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.study_show_meaning))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun FinishedContent(onFinish: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(72.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.study_session_done),
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onFinish) {
                Text(stringResource(R.string.study_finish))
            }
        }
    }
}
