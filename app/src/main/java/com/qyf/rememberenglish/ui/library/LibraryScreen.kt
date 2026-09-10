package com.qyf.rememberenglish.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.data.online.OnlineWord
import com.qyf.rememberenglish.data.repository.SearchHit
import com.qyf.rememberenglish.ui.components.WordRow

/**
 * 词库：搜索 + 列表（点击进详情，+ 加词）。
 * 模糊匹配置顶（用户 2026-09-08）；本地查不到时自动联网兜底。
 */
@Composable
fun LibraryScreen(
    onWordClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.library_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            // 一键清空（用户 2026-09-08 要求）
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { viewModel.onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.library_clear),
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
        )

        when {
            state.query.isBlank() -> EmptyHint(stringResource(R.string.library_search_empty))
            state.results.isNotEmpty() -> LazyColumn {
                // 本地无精确匹配时也联网兜底，结果置顶显示（用户 2026-09-10）
                when (val online = state.online) {
                    OnlineLookupState.Loading -> item(key = "online-loading") {
                        Text(
                            text = stringResource(R.string.library_online_loading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                    is OnlineLookupState.Found -> item(key = "online-found") {
                        OnlineResultContent(
                            online = online.word,
                            added = state.onlineAdded,
                            onAdd = viewModel::addOnlineWordToMine,
                        )
                    }
                    else -> {}
                }
                items(state.results, key = { "hit-${it.word.id}" }) { hit ->
                    Column {
                        WordRow(
                            word = hit.word,
                            inMine = hit.word.id in state.inMineIds,
                            onClick = { onWordClick(hit.word.id) },
                            onAdd = { viewModel.addToMine(hit.word.id) },
                        )
                        hit.note?.let { note ->
                            Text(
                                text = note,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            }
            state.online is OnlineLookupState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.library_online_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.online is OnlineLookupState.Found -> LazyColumn {
                item {
                    OnlineResultContent(
                        online = (state.online as OnlineLookupState.Found).word,
                        added = state.onlineAdded,
                        onAdd = viewModel::addOnlineWordToMine,
                    )
                }
            }
            else -> EmptyHint(stringResource(R.string.library_no_result))
        }
    }
}

/** 在线结果内容（无滚动容器，可嵌入本地结果列表或独立展示），可一键入库加入我要背 */
@Composable
private fun OnlineResultContent(
    online: com.qyf.rememberenglish.data.online.OnlineWord,
    added: Boolean,
    onAdd: () -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.library_online_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = online.word,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (online.usphone.isNotBlank()) {
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        Text(
                            text = "/${online.usphone}/",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                online.meanings.forEach { meaning ->
                    Text(
                        text = meaning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            // 与本地结果行一致（WordRow）：+ 加入 / ✓ 已加入，保持界面协调（用户 2026-09-10）
            IconButton(onClick = onAdd, enabled = !added) {
                Icon(
                    imageVector = if (added) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (added) R.string.detail_in_mine else R.string.detail_not_in_mine,
                    ),
                    tint = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
