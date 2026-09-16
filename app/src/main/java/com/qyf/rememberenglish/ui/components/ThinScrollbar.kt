package com.qyf.rememberenglish.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp

/**
 * 细滚动条（用户 2026-09-16：所有可下拉界面右侧一条细的，深浅色各一套且协调）。
 *
 * **为什么用 Modifier 扩展而不是套一层 Box 浮一个组件**：
 * 状态在**绘制阶段**才读（`drawWithContent` 的 lambda 内），滚动时只触发**重绘**、不触发重组，
 * 长列表滚动不会因为滚动条而整屏重组；同时也不必给现有界面加缩进层级。
 *
 * **颜色的"两套"怎么做**：不硬编码两组 RGB。App 的深色模式是**换主题**，
 * 颜色取 `MaterialTheme.colorScheme.onSurfaceVariant`（浅色下是深灰、深色下是浅灰）就会自动跟着切，
 * 任何主题调整下都协调；写死颜色反而会在以后改主题时脱节。
 *
 * 使用时把 `.thinScrollbar(state)` 放在 `.padding(...)` **之前**（更靠近滚动容器），
 * 这样滚动条贴的是容器右缘而不是被内边距推离边缘。
 */
private val BAR_WIDTH = 3.dp
private val MIN_THUMB = 24.dp
private val TRACK_VERTICAL_INSET = 4.dp
private val BAR_END_INSET = 2.dp
private const val BAR_ALPHA = 0.45f

/** 竖向滚动条（`Column` + `verticalScroll`）：视口高取 [ScrollState.viewportSize]（1.7.6 已有该公开 API） */
@Composable
fun Modifier.thinScrollbar(state: ScrollState): Modifier {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = BAR_ALPHA)
    return this.drawWithContent {
        drawContent()
        val max = state.maxValue
        val viewport = state.viewportSize
        if (max <= 0 || viewport <= 0) return@drawWithContent
        drawThumb(
            position = (state.value.toFloat() / max).coerceIn(0f, 1f),
            visibleFraction = (viewport.toFloat() / (viewport + max)).coerceIn(MIN_FRACTION, 1f),
            color = color,
        )
    }
}

/** 竖向滚动条（`LazyColumn`）：懒列表拿不到内容总高度，用条目数近似（本项目行高接近一致，误差可忽略） */
@Composable
fun Modifier.thinScrollbar(state: LazyListState): Modifier {
    val color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = BAR_ALPHA)
    return this.drawWithContent {
        drawContent()
        val layoutInfo = state.layoutInfo
        val total = layoutInfo.totalItemsCount
        val visible = layoutInfo.visibleItemsInfo
        // 未测量完 / 内容没超出一屏 → 不画
        if (total <= 0 || visible.isEmpty() || visible.size >= total) return@drawWithContent
        val first = visible.first()
        // 首条已被滚过去的比例，让拇指在条目之间也能连续移动
        val intra = if (first.size > 0) {
            ((layoutInfo.viewportStartOffset - first.offset).toFloat() / first.size).coerceIn(0f, 1f)
        } else {
            0f
        }
        val travel = (total - visible.size).coerceAtLeast(1)
        drawThumb(
            position = ((first.index + intra) / travel).coerceIn(0f, 1f),
            visibleFraction = (visible.size.toFloat() / total).coerceIn(MIN_FRACTION, 1f),
            color = color,
        )
    }
}

private const val MIN_FRACTION = 0.05f

/** 在容器右缘画一条 3dp 圆角拇指；位置与长度都由 0..1 的比例决定 */
private fun DrawScope.drawThumb(position: Float, visibleFraction: Float, color: Color) {
    val barWidth = BAR_WIDTH.toPx()
    val inset = TRACK_VERTICAL_INSET.toPx()
    val track = size.height - inset * 2
    if (track <= 0f) return
    val minThumb = MIN_THUMB.toPx().coerceAtMost(track)
    val thumbHeight = (track * visibleFraction).coerceIn(minThumb, track)
    val top = inset + (track - thumbHeight) * position
    drawRoundRect(
        color = color,
        topLeft = Offset(x = size.width - barWidth - BAR_END_INSET.toPx(), y = top),
        size = Size(width = barWidth, height = thumbHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
    )
}
