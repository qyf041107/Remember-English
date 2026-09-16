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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.data.online.OnlineWord
import com.qyf.rememberenglish.data.repository.SearchHit
import com.qyf.rememberenglish.ui.components.RowTailAction
import com.qyf.rememberenglish.ui.components.thinScrollbar
import com.qyf.rememberenglish.ui.components.WordRow

/**
 * 词库：搜索 + 列表（点击进详情，行尾 黑名单 | 星标 | 加入）。
 * 模糊匹配置顶（用户 2026-09-08）；本地查不到时自动联网兜底，在线结果同样可点开详情（用户 2026-09-16）。
 */
@Composable
fun LibraryScreen(
    onWordClick: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    // 三个结果分支互斥，共用同一份滚动状态即可（细滚动条见下）
    val resultsState = rememberLazyListState()

    // 拉黑（用户 2026-09-16）：Snackbar 可撤销
    LaunchedEffect(state.blacklistedWord) {
        state.blacklistedWord?.let { word ->
            val action = snackbarHostState.showSnackbar(
                message = context.getString(R.string.add_blacklisted, word),
                actionLabel = context.getString(R.string.mine_undo),
            )
            viewModel.consumeBlacklistedWord()
            if (action == SnackbarResult.ActionPerformed) viewModel.unblacklistWord(word)
        }
    }
    // 点星标时若该词还没在"我要背"，会自动加入：提示一次
    LaunchedEffect(state.starNotice) {
        state.starNotice?.let { word ->
            snackbarHostState.showSnackbar(context.getString(R.string.add_star_added, word))
            viewModel.consumeStarNotice()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                state.results.isNotEmpty() -> LazyColumn(
                    state = resultsState,
                    modifier = Modifier.thinScrollbar(resultsState),
                ) {
                    // 查询词本身被拉黑：只提示一句，不能把 they/their/there 这些模糊结果一起吞掉
                    if (state.queryBlacklisted) {
                        item(key = "blacklisted-note") {
                            Text(
                                text = stringResource(R.string.library_blacklisted),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
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
                                starred = state.onlineStarred,
                                onClick = { viewModel.openOnlineWordDetail(onWordClick) },
                                onAdd = viewModel::toggleOnlineMine,
                                onStar = viewModel::starOnlineWord,
                                onBlacklist = viewModel::blacklistOnlineWord,
                            )
                        }
                        is OnlineLookupState.Suggestions -> item(key = "online-suggest") {
                            SuggestionContent(
                                suggestions = online,
                                added = state.suggestionAdded,
                                starred = state.suggestionStarred,
                                onClick = { viewModel.openSuggestionDetail(it, onWordClick) },
                                onAdd = viewModel::toggleSuggestionMine,
                                onStar = viewModel::toggleSuggestionStar,
                                onBlacklist = viewModel::blacklistSuggestion,
                            )
                        }
                        else -> {}
                    }
                    items(state.results, key = { "hit-${it.word.id}" }) { hit ->
                        WordRow(
                            word = hit.word,
                            inMine = hit.word.id in state.inMineIds,
                            starred = hit.word.id in state.starredIds,
                            onClick = { onWordClick(hit.word.id) },
                            onToggleMine = { viewModel.toggleMine(hit.word.id) },
                            onStar = { viewModel.toggleStar(hit.word.id, hit.word.word) },
                            onBlacklist = { viewModel.blacklistWord(hit.word.word) },
                            modifier = Modifier.padding(bottom = 2.dp),
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
                // 黑名单词且无其它结果：明说原因，否则用户以为搜索坏了
                state.queryBlacklisted -> EmptyHint(stringResource(R.string.library_blacklisted))
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
                state.online is OnlineLookupState.Found -> LazyColumn(
                    state = resultsState,
                    modifier = Modifier.thinScrollbar(resultsState),
                ) {
                    item {
                        OnlineResultContent(
                            online = (state.online as OnlineLookupState.Found).word,
                            added = state.onlineAdded,
                            starred = state.onlineStarred,
                            onClick = { viewModel.openOnlineWordDetail(onWordClick) },
                            onAdd = viewModel::toggleOnlineMine,
                            onStar = viewModel::starOnlineWord,
                            onBlacklist = viewModel::blacklistOnlineWord,
                        )
                    }
                }
                // 拼错了：本地与联网精确查都没结果，给出纠错建议（用户 2026-09-16）
                state.online is OnlineLookupState.Suggestions -> LazyColumn(
                    state = resultsState,
                    modifier = Modifier.thinScrollbar(resultsState),
                ) {
                    item {
                        SuggestionContent(
                            suggestions = state.online as OnlineLookupState.Suggestions,
                            added = state.suggestionAdded,
                            starred = state.suggestionStarred,
                            onClick = { viewModel.openSuggestionDetail(it, onWordClick) },
                            onAdd = viewModel::toggleSuggestionMine,
                            onStar = viewModel::toggleSuggestionStar,
                            onBlacklist = viewModel::blacklistSuggestion,
                        )
                    }
                }
                else -> EmptyHint(stringResource(R.string.library_no_result))
            }
        }

        // 放顶部：搜索时键盘会挡住底部 Snackbar（与扫词页保持一致）
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding(),
        )
    }
}

/**
 * 在线结果行（用户 2026-09-16：可点开详情，行尾与本地行一致）
 * 点击 → 落库为自定义词后跳转现有详情页；无需单独的"在线词详情"页面。
 */
@Composable
private fun OnlineResultContent(
    online: OnlineWord,
    added: Boolean,
    starred: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    onStar: () -> Unit,
    onBlacklist: () -> Unit,
) {
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
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
                .clickable(onClick = onClick)
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
                        Spacer(modifier = Modifier.width(8.dp))
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
            RowTailAction(
                icon = ImageVector.vectorResource(R.drawable.ic_block),
                contentDescription = stringResource(R.string.add_blacklist),
                onClick = onBlacklist,
            )
            RowTailAction(
                icon = if (starred) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = stringResource(
                    if (starred) R.string.add_unstar else R.string.add_star,
                ),
                tint = if (starred) MaterialTheme.colorScheme.tertiary else inactive,
                onClick = onStar,
            )
            RowTailAction(
                icon = if (added) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = stringResource(
                    if (added) R.string.detail_remove else R.string.detail_not_in_mine,
                ),
                tint = if (added) MaterialTheme.colorScheme.primary else inactive,
                // 已在"我要背"也保持可点：再点一次即撤回（用户 2026-09-16）
                pressedScale = if (added) 0.75f else 1.35f,
                onClick = onAdd,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}

/**
 * 拼写建议区块（用户 2026-09-16）：本地与联网精确查都没结果时，给出有道纠错候选。
 * 建议词已回填完整释义（suggest 自带的 explain 带截断），行尾同样三件套。
 */
@Composable
private fun SuggestionContent(
    suggestions: OnlineLookupState.Suggestions,
    added: Set<String>,
    starred: Set<String>,
    onClick: (String) -> Unit,
    onAdd: (String) -> Unit,
    onStar: (String) -> Unit,
    onBlacklist: (String) -> Unit,
) {
    val inactive = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Text(
            text = stringResource(R.string.library_suggest_title, suggestions.original),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        suggestions.words.forEach { word ->
            val isAdded = word.word in added
            val isStarred = word.word in starred
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(word.word) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = word.word,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (word.usphone.isNotBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "/${word.usphone}/",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    word.meanings.firstOrNull()?.let { meaning ->
                        Text(
                            text = meaning,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                RowTailAction(
                    icon = ImageVector.vectorResource(R.drawable.ic_block),
                    contentDescription = stringResource(R.string.add_blacklist),
                    onClick = { onBlacklist(word.word) },
                )
                RowTailAction(
                    icon = if (isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        if (isStarred) R.string.add_unstar else R.string.add_star,
                    ),
                    tint = if (isStarred) MaterialTheme.colorScheme.tertiary else inactive,
                    onClick = { onStar(word.word) },
                )
                RowTailAction(
                    icon = if (isAdded) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (isAdded) R.string.detail_remove else R.string.detail_not_in_mine,
                    ),
                    tint = if (isAdded) MaterialTheme.colorScheme.primary else inactive,
                    // 已在"我要背"也保持可点：再点一次即撤回（用户 2026-09-16）
                    pressedScale = if (isAdded) 0.75f else 1.35f,
                    onClick = { onAdd(word.word) },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
        }
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
