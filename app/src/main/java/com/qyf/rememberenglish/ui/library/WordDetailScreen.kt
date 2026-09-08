package com.qyf.rememberenglish.ui.library

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.domain.model.Word

/** 词详情：单词/音标/释义/发音 + 加入或移出我要背 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun WordDetailScreen(
    onBack: () -> Unit,
    viewModel: WordDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val word = state.word

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(word?.word.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.detail_back))
                    }
                },
            )
        },
    ) { padding ->
        if (word == null) {
            Text(
                text = stringResource(R.string.detail_not_found),
                modifier = Modifier
                    .padding(padding)
                    .padding(32.dp),
            )
            return@Scaffold
        }
        WordDetailContent(
            word = word,
            inMine = state.inMine,
            freq = state.freq,
            onToggleMine = viewModel::toggleMine,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        )
    }
}

@Composable
private fun WordDetailContent(
    word: Word,
    inMine: Boolean,
    freq: Int?,
    onToggleMine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = word.word,
            style = MaterialTheme.typography.displayMedium,
        )
        if (word.usphone.isNotBlank() || word.ukphone.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    if (word.usphone.isNotBlank()) append("美 /${word.usphone}/")
                    if (word.ukphone.isNotBlank()) {
                        if (isNotEmpty()) append("  ")
                        append("英 /${word.ukphone}/")
                    }
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        freq?.let {
            Text(
                text = stringResource(R.string.detail_freq, it),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        word.meanings.forEach { meaning ->
            Text(
                text = meaning,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
        if (word.isCustom) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.detail_custom_tag),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.height(24.dp))
        if (inMine) {
            OutlinedButton(
                onClick = onToggleMine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) { Text(stringResource(R.string.detail_remove)) }
        } else {
            Button(
                onClick = onToggleMine,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            ) { Text(stringResource(R.string.detail_add)) }
        }
    }
}
