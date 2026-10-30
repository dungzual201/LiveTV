package com.livetv.vn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.model.Program
import com.livetv.vn.playback.PlaybackSnapshot
import com.livetv.vn.playback.PlaybackStatus
import com.livetv.vn.ui.channelNumber
import com.livetv.vn.ui.formatClock
import com.livetv.vn.ui.formatRange
import com.livetv.vn.ui.formatRemaining
import com.livetv.vn.ui.theme.LiveColors

@Composable
fun LiveInfoCard(
    channel: Channel,
    snapshot: PlaybackSnapshot,
    currentProgram: Program?,
    nextProgram: Program?,
    now: Long,
    isFavorite: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 650.dp),
        color = Color(0xFF111418),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(channel = channel, size = 58.dp)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "KÊNH ${channelNumber(channel.number)}",
                            color = LiveColors.TextMuted,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(Modifier.width(10.dp))
                        LiveBadge()
                        if (isFavorite) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "★",
                                color = LiveColors.Focus,
                                fontSize = 16.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = channel.name,
                        color = LiveColors.Text,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (currentProgram != null) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = currentProgram.title,
                    color = LiveColors.Text,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                val duration = (currentProgram.endMs - currentProgram.startMs).coerceAtLeast(1L)
                val fraction = ((now - currentProgram.startMs).toFloat() / duration)
                    .coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .background(LiveColors.Live),
                    )
                }
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatRange(currentProgram.startMs, currentProgram.endMs),
                        color = LiveColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatRemaining(currentProgram.endMs, now),
                        color = LiveColors.TextMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (currentProgram.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = currentProgram.description,
                        color = LiveColors.TextMuted,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (nextProgram != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Tiếp theo · ${formatClock(nextProgram.startMs)}  ${nextProgram.title}",
                        color = LiveColors.TextDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (nextProgram != null) {
                        "Sắp phát · ${formatClock(nextProgram.startMs)}  ${nextProgram.title}"
                    } else {
                        "Chưa có lịch phát sóng cho kênh này"
                    },
                    color = LiveColors.TextMuted,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val quality = snapshot.qualityLabel
                if (!quality.isNullOrBlank()) {
                    Text(
                        text = quality,
                        color = LiveColors.Focus,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .border(1.dp, LiveColors.Focus.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = when (snapshot.status) {
                        PlaybackStatus.CONNECTING -> "Đang kết nối"
                        PlaybackStatus.FAILOVER -> "Đang chuyển nguồn dự phòng"
                        else -> "Đang phát"
                    },
                    color = LiveColors.TextDim,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "· Nguồn ${snapshot.attempt.coerceAtLeast(1)}/${snapshot.total.coerceAtLeast(1)}",
                    color = LiveColors.TextDim,
                    fontSize = 11.sp,
                )
                snapshot.sourceName?.let {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "· $it",
                        color = LiveColors.TextDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(LiveColors.Live.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(LiveColors.Live),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "LIVE",
            color = LiveColors.Live,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
fun BufferingPill(
    snapshot: PlaybackSnapshot,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.widthIn(max = 540.dp),
        color = LiveColors.Panel,
        shape = RoundedCornerShape(30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f)),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = LiveColors.Live,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = if (snapshot.status == PlaybackStatus.FAILOVER) {
                        "Đang thử nguồn dự phòng ${snapshot.attempt}/${snapshot.total}"
                    } else {
                        "Đang kết nối nguồn ${snapshot.attempt.coerceAtLeast(1)}/${snapshot.total.coerceAtLeast(1)}"
                    },
                    color = LiveColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
                val subtitle = listOfNotNull(snapshot.sourceName, snapshot.qualityLabel).joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        color = LiveColors.TextDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun PlaybackErrorOverlay(
    channel: Channel?,
    snapshot: PlaybackSnapshot,
    selectedAction: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.64f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.widthIn(max = 620.dp),
            color = LiveColors.Panel,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shadowElevation = 24.dp,
        ) {
            Column(modifier = Modifier.padding(28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (channel != null) {
                        ChannelLogo(channel = channel, size = 46.dp)
                        Spacer(Modifier.width(14.dp))
                    }
                    Column {
                        Text(
                            text = "Không phát được kênh này",
                            color = LiveColors.Text,
                            fontSize = 23.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (channel != null) {
                            Text(
                                text = "${channelNumber(channel.number)} · ${channel.name}",
                                color = LiveColors.TextMuted,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = snapshot.error ?: "Nguồn phát không khả dụng",
                    color = LiveColors.TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Đã thử ${snapshot.attempt}/${snapshot.total} nguồn của kênh.",
                    color = LiveColors.TextDim,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ErrorActionChip(
                        label = "Thử lại",
                        selected = selectedAction == 0,
                    )
                    ErrorActionChip(
                        label = "Danh sách kênh",
                        selected = selectedAction == 1,
                    )
                }
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "◀▶ chọn · OK xác nhận · ▲▼ đổi kênh · MENU cài đặt",
                    color = LiveColors.TextDim,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ErrorActionChip(label: String, selected: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) LiveColors.Text else Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (selected) LiveColors.Text else Color.White.copy(alpha = 0.1f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            color = if (selected) LiveColors.Background else LiveColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ToastPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.widthIn(max = 560.dp),
        color = LiveColors.PanelSolid,
        shape = RoundedCornerShape(30.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        shadowElevation = 12.dp,
    ) {
        Text(
            text = text,
            color = LiveColors.Text,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 11.dp),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun StartupContent(
    initialized: Boolean,
    syncing: Boolean,
    errors: List<String>,
    modifier: Modifier = Modifier,
) {
    val loading = !initialized || syncing
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF171B21),
                        LiveColors.Background,
                    ),
                    radius = 950f,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 640.dp).padding(horizontal = 32.dp),
        ) {
            BrandMark(modifier = Modifier.size(84.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                text = "LiveTV",
                color = LiveColors.Text,
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Truyền hình trực tuyến",
                color = LiveColors.TextMuted,
                fontSize = 14.sp,
                letterSpacing = 1.4.sp,
            )
            Spacer(Modifier.height(36.dp))
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    color = LiveColors.Live,
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Đang tải danh sách kênh...",
                    color = LiveColors.Text,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Kết nối tới các nguồn IPTV và lịch phát sóng",
                    color = LiveColors.TextDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = if (errors.isEmpty()) "Chưa có kênh nào" else "Chưa tải được danh sách kênh",
                    color = LiveColors.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
                if (errors.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = errors.take(3).joinToString("\n"),
                        color = LiveColors.TextMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "OK thử lại · MENU cài đặt",
                    color = LiveColors.TextDim,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
