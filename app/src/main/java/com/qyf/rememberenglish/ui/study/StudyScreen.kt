package com.qyf.rememberenglish.ui.study

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import com.qyf.rememberenglish.domain.model.AnswerRating
import com.qyf.rememberenglish.domain.model.DailyProgress

/** 今日学习：进度 + 加权随机会话（英文优先、点屏显义、三键记分）（CLAUDE.md 第五节） */
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
            is SessionState.Studying -> StudyingContent(s, state.nextInSeconds, viewModel)
            SessionState.Finished -> FinishedContent(onFinish = viewModel::quit)
        }
    }
}

@Composable
private fun ProgressCard(progress: DailyProgress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(
                    R.string.study_progress,
                    progress.masteredToday.coerceAtMost(progress.target),
                    progress.target,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (progress.target == 0) 1f
                    else (progress.masteredToday.toFloat() / progress.target).coerceIn(0f, 1f)
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
            if (progress != null && progress.isDone) {
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
                    text = stringResource(R.string.study_done_body, progress.masteredToday),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else if (!state.hasWords) {
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
private fun StudyingContent(
    s: SessionState.Studying,
    nextInSeconds: Int,
    viewModel: StudyViewModel,
) {
    val word = s.word
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = viewModel::quit) {
                Text(stringResource(R.string.study_quit))
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable {
                    if (s.awaitingNext) viewModel.skipWait() else viewModel.reveal()
                },
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
                    Text(
                        text = stringResource(R.string.study_reveal_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (s.awaitingNext) {
            // 答错/不清楚：停留展示释义，倒计时结束自动进入下一个（点击卡片可跳过）
            Text(
                text = stringResource(R.string.study_next_in, nextInSeconds),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { viewModel.rate(AnswerRating.KNOW) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_know))
                }
                OutlinedButton(
                    onClick = { viewModel.rate(AnswerRating.WRONG) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_wrong), color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick = { viewModel.rate(AnswerRating.UNCLEAR) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_unclear))
                }
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
