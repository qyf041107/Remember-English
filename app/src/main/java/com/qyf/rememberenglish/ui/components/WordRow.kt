package com.qyf.rememberenglish.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.domain.model.Word

/**
 * 词库/列表通用行：单词 + 音标 + 首条释义 + 行尾操作。
 *
 * 行尾统一三件套（用户 2026-09-16）：**黑名单 | 星标 | 加入我要背**，三个控件同一套紧凑热区
 * （图标 20dp、热区 34dp），避免尺寸不一互相"打架"。
 * [trailing] 非空时完全接管行尾（"我要背"列表用它放分数标签与左滑前景）。
 */
@Composable
fun WordRow(
    word: Word,
    inMine: Boolean,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    starred: Boolean = false,
    onStar: (() -> Unit)? = null,
    onBlacklist: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                Spacer(modifier = Modifier.width(8.dp))
                if (word.usphone.isNotBlank()) {
                    Text(
                        text = "/${word.usphone}/",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            if (word.meanings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = word.meanings.take(2).joinToString("；"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            val inactive = MaterialTheme.colorScheme.onSurfaceVariant
            // 黑名单：拉黑后不再出现在扫词候选与词库搜索（"我的 → 词黑名单"可恢复）
            onBlacklist?.let { onBlacklistClick ->
                RowTailAction(
                    icon = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.add_blacklist),
                    onClick = onBlacklistClick,
                )
            }
            // 星标：永不算已掌握 + 抽中权重 ×3；未加入"我要背"时点它=自动加入并打星
            onStar?.let { onStarClick ->
                RowTailAction(
                    icon = if (starred) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = stringResource(
                        if (starred) R.string.add_unstar else R.string.add_star,
                    ),
                    tint = if (starred) MaterialTheme.colorScheme.tertiary else inactive,
                    onClick = onStarClick,
                )
            }
            // 加词：配色沿用用户已检阅的样子（已加入=primary 对勾）
            RowTailAction(
                icon = if (inMine) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = stringResource(
                    if (inMine) R.string.detail_in_mine else R.string.detail_not_in_mine,
                ),
                tint = if (inMine) MaterialTheme.colorScheme.primary else inactive,
                enabled = !inMine,
                onClick = onAdd,
            )
        }
    }
}

/** 行尾操作图标：统一 20dp 图标 + 34dp 热区，三个并排尺寸一致（在线结果行也复用） */
@Composable
internal fun RowTailAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
