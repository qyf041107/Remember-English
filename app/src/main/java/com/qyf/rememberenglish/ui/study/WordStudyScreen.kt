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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.qyf.rememberenglish.domain.srs.ScoreConstants

/** 单词背诵：只显示英文 → 点卡片显示释义 → 我知道/我不会/不清楚（CLAUDE.md 第五节） */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WordStudyScreen(
    onBack: () -> Unit,
    viewModel: WordStudyViewModel = hiltViewModel(),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.study_single_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.detail_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.notFound -> Text(
                text = stringResource(R.string.detail_not_found),
                modifier = Modifier
                    .padding(padding)
                    .padding(32.dp),
            )
            state.userWord == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            else -> WordStudyContent(
                state = state,
                onReveal = viewModel::reveal,
                onRate = viewModel::rate,
                onBack = onBack,
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun WordStudyContent(
    state: WordStudyUiState,
    onReveal: () -> Unit,
    onRate: (AnswerRating) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val word = state.word
    Column(
        modifier = modifier.padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(enabled = !state.revealed, onClick = onReveal),
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
                if (state.revealed) {
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
        val userWord = state.userWord
        if (state.rated == null || userWord == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { onRate(AnswerRating.KNOW) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_know))
                }
                OutlinedButton(
                    onClick = { onRate(AnswerRating.WRONG) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_wrong), color = MaterialTheme.colorScheme.error)
                }
                OutlinedButton(
                    onClick = { onRate(AnswerRating.UNCLEAR) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.study_unclear))
                }
            }
        } else {
            // 记分后显示结果：当前背分 + 是否已掌握，完成返回列表
            Text(
                text = stringResource(
                    R.string.mine_score,
                    formatScore(userWord.score),
                    ScoreConstants.MASTER_SCORE.toInt(),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (userWord.isMastered) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.mine_stat_mastered),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.study_finish))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

/** 整数不带小数点（如 3/5、4.5/5） */
private fun formatScore(score: Double): String =
    if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
