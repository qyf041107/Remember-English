package com.qyf.rememberenglish.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qyf.rememberenglish.R
import com.qyf.rememberenglish.domain.model.Word
import kotlinx.coroutines.launch

/**
 * 词库/列表通用行：单词 + 音标 + 首条释义 + 行尾操作。
 *
 * 行尾统一三件套（用户 2026-09-16）：**黑名单 | 星标 | 加入我要背**，三个控件同一套紧凑热区
 * （图标 20dp、热区 34dp），避免尺寸不一互相"打架"。
 * [trailing] 非空时完全接管行尾（"我要背"列表用它放分数标签与左滑前景），此时 [onToggleMine]
 * 与 [inMine] 都不会被渲染。
 */
@Composable
fun WordRow(
    word: Word,
    inMine: Boolean,
    onClick: () -> Unit,
    onToggleMine: (() -> Unit)? = null,
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
                    icon = ImageVector.vectorResource(R.drawable.ic_block),
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
            // 加入/取消加入（用户 2026-09-16）：**始终可点**，再点一次即撤回误加的
            // contentDescription 用"动作"而非"状态"——读屏时"已加入我要背"不会告诉用户点了会怎样
            onToggleMine?.let { onToggle ->
                RowTailAction(
                    icon = if (inMine) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = stringResource(
                        if (inMine) R.string.detail_remove else R.string.detail_not_in_mine,
                    ),
                    tint = if (inMine) MaterialTheme.colorScheme.primary else inactive,
                    // 取消时先缩后回弹（读起来是"被取走"），加入时弹大过冲
                    pressedScale = if (inMine) 0.75f else 1.35f,
                    onClick = onToggle,
                )
            }
        }
    }
}

/**
 * 行尾操作图标：统一 20dp 图标 + 34dp 热区，三个并排尺寸一致（在线结果行/拼写建议行/黑名单行都复用）。
 *
 * 点击动画（用户 2026-09-16 要求，仿 B 站点赞投币收藏的弹跳感）：先缩放到 [pressedScale]
 * 再用回弹弹簧落回 1.0，过冲带来"点到了"的手感。加入传 1.35（弹大），取消传 0.75（先缩，
 * 读起来是"被取走"）。颜色另走 [animateColorAsState]，让描边星↔实心星、+↔✓ 平滑过渡而不是硬切。
 */
@Composable
internal fun RowTailAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    /** 点击时先去到哪个缩放值：加入=弹大 1.35，取消=先缩 0.75 */
    pressedScale: Float = 1.35f,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val animatedTint by animateColorAsState(targetValue = tint, label = "rowTailTint")

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled) {
                onClick()
                scope.launch {
                    scale.animateTo(
                        targetValue = pressedScale,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh,
                        ),
                    )
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = animatedTint,
            modifier = Modifier
                .size(20.dp)
                .scale(scale.value),
        )
    }
}
