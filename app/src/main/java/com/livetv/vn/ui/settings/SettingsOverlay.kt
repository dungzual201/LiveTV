package com.livetv.vn.ui.settings

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livetv.vn.data.model.ChannelCatalog
import com.livetv.vn.data.model.EpgUi
import com.livetv.vn.data.model.PlaylistUi
import com.livetv.vn.data.model.QualityMode
import com.livetv.vn.data.model.SourcesUi
import com.livetv.vn.ui.KeyRouter
import com.livetv.vn.ui.formatDateTime
import com.livetv.vn.ui.theme.LiveColors
import kotlinx.coroutines.launch

private sealed interface SettingsAction {
    data class TogglePlaylist(val item: PlaylistUi) : SettingsAction
    data class ToggleEpg(val item: EpgUi) : SettingsAction
    data class SetQuality(val mode: QualityMode) : SettingsAction
    data object Refresh : SettingsAction
    data object ClearCache : SettingsAction
    data object ResetSources : SettingsAction
    data object Legal : SettingsAction
    data object Close : SettingsAction
}

private data class SettingsRow(
    val key: String,
    val section: String? = null,
    val title: String,
    val subtitle: String,
    val value: String? = null,
    val valueActive: Boolean = false,
    val action: SettingsAction,
)

@Composable
fun SettingsOverlay(
    sources: SourcesUi,
    catalog: ChannelCatalog,
    favoritesCount: Int,
    qualityMode: QualityMode,
    catalogSyncing: Boolean,
    epgSyncing: Boolean,
    epgUpdatedAt: Long,
    epgError: String?,
    refreshing: Boolean,
    onClose: () -> Unit,
    onTogglePlaylist: (String, Boolean) -> Unit,
    onToggleEpg: (String, Boolean) -> Unit,
    onSetQualityMode: (QualityMode) -> Unit,
    onRefresh: () -> Unit,
    onClearCache: () -> Unit,
    onResetSources: () -> Unit,
) {
    val rows = buildList {
        if (sources.playlists.isEmpty()) {
            add(
                SettingsRow(
                    key = "source-empty",
                    section = "NGUỒN KÊNH",
                    title = "Chưa bật nguồn kênh",
                    subtitle = "Bật ít nhất một nguồn để tải danh sách kênh",
                    action = SettingsAction.Refresh,
                ),
            )
        } else {
            sources.playlists.forEachIndexed { index, item ->
                add(
                    SettingsRow(
                        key = "playlist:${item.source.id}",
                        section = if (index == 0) "NGUỒN KÊNH" else null,
                        title = item.source.name,
                        subtitle = buildString {
                            append("Ưu tiên ${item.source.priority}")
                            if (item.custom) append(" · tùy chỉnh")
                            if (item.enabled) append(" · đang hoạt động")
                        },
                        value = if (item.enabled) "BẬT" else "TẮT",
                        valueActive = item.enabled,
                        action = SettingsAction.TogglePlaylist(item),
                    ),
                )
            }
        }

        if (sources.epgs.isEmpty()) {
            add(
                SettingsRow(
                    key = "epg-empty",
                    section = "LỊCH PHÁT SÓNG",
                    title = "Chưa bật nguồn EPG",
                    subtitle = "Bật EPG để xem lịch phát sóng",
                    action = SettingsAction.Refresh,
                ),
            )
        } else {
            sources.epgs.forEachIndexed { index, item ->
                add(
                    SettingsRow(
                        key = "epg:${item.source.id}",
                        section = if (index == 0) "LỊCH PHÁT SÓNG" else null,
                        title = item.source.name,
                        subtitle = buildString {
                            append("Ưu tiên ${item.source.priority}")
                            if (item.custom) append(" · tùy chỉnh")
                            if (item.enabled) append(" · đang hoạt động")
                        },
                        value = if (item.enabled) "BẬT" else "TẮT",
                        valueActive = item.enabled,
                        action = SettingsAction.ToggleEpg(item),
                    ),
                )
            }
        }

        add(
            SettingsRow(
                key = "quality",
                section = "CHẤT LƯỢNG PHÁT",
                title = "Chế độ chọn nguồn",
                subtitle = "Áp dụng khi đổi kênh hoặc thử lại nguồn",
                value = qualityMode.label(),
                valueActive = true,
                action = SettingsAction.SetQuality(nextQuality(qualityMode)),
            ),
        )

        add(
            SettingsRow(
                key = "refresh",
                section = "DỮ LIỆU",
                title = if (refreshing) "Đang làm mới dữ liệu..." else "Làm mới kênh và lịch phát sóng",
                subtitle = "Cập nhật lúc ${formatDateTime(catalog.updatedAt)} · ${catalog.channels.size} kênh",
                action = SettingsAction.Refresh,
            ),
        )
        add(
            SettingsRow(
                key = "clear-cache",
                title = "Xóa dữ liệu tạm",
                subtitle = "Tải lại nguồn và EPG, vẫn giữ yêu thích",
                action = SettingsAction.ClearCache,
            ),
        )
        add(
            SettingsRow(
                key = "reset-sources",
                title = "Đặt lại nguồn mặc định",
                subtitle = "Bật lại cấu hình nguồn gốc của ứng dụng",
                action = SettingsAction.ResetSources,
            ),
        )

        add(
            SettingsRow(
                key = "legal",
                section = "KHÁC",
                title = "Giới thiệu & pháp lý",
                subtitle = "Nguồn công khai, bản quyền và điều khoản sử dụng",
                action = SettingsAction.Legal,
            ),
        )
        add(
            SettingsRow(
                key = "close",
                title = "Đóng cài đặt",
                subtitle = "Quay lại màn hình xem truyền hình",
                action = SettingsAction.Close,
            ),
        )
    }

    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedIndex = rows.indexOfFirst { it.key == selectedKey }
        .takeIf { it >= 0 } ?: 0
    var legalVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val legalScroll = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(rows.map { it.key }) {
        if (rows.isEmpty()) return@LaunchedEffect
        if (selectedKey == null || rows.none { it.key == selectedKey }) {
            selectedKey = rows.first().key
        }
    }
    LaunchedEffect(selectedIndex, legalVisible) {
        if (legalVisible) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty() || selectedIndex !in visible.first().index..visible.last().index) {
            listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
        }
    }

    fun perform(row: SettingsRow) {
        when (val action = row.action) {
            is SettingsAction.TogglePlaylist ->
                onTogglePlaylist(action.item.source.id, !action.item.enabled)
            is SettingsAction.ToggleEpg ->
                onToggleEpg(action.item.source.id, !action.item.enabled)
            is SettingsAction.SetQuality -> onSetQualityMode(action.mode)
            SettingsAction.Refresh -> onRefresh()
            SettingsAction.ClearCache -> onClearCache()
            SettingsAction.ResetSources -> onResetSources()
            SettingsAction.Legal -> legalVisible = true
            SettingsAction.Close -> onClose()
        }
    }

    fun handleKey(event: KeyEvent): Boolean {
        val code = event.nativeKeyEvent.keyCode
        val relevant = code == AndroidKeyEvent.KEYCODE_DPAD_UP ||
            code == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
            code == AndroidKeyEvent.KEYCODE_CHANNEL_UP ||
            code == AndroidKeyEvent.KEYCODE_CHANNEL_DOWN ||
            code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            code == AndroidKeyEvent.KEYCODE_ENTER ||
            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        if (!relevant) return false
        if (event.type != KeyEventType.KeyDown) return true

        if (legalVisible) {
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_UP -> scope.launch { legalScroll.animateScrollBy(-90f) }
                AndroidKeyEvent.KEYCODE_DPAD_DOWN,
                AndroidKeyEvent.KEYCODE_CHANNEL_UP,
                AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
                -> scope.launch { legalScroll.animateScrollBy(90f) }
                AndroidKeyEvent.KEYCODE_DPAD_CENTER,
                AndroidKeyEvent.KEYCODE_ENTER,
                AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
                -> legalVisible = false
            }
            return true
        }

        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP,
            AndroidKeyEvent.KEYCODE_CHANNEL_UP,
            -> {
                val next = (selectedIndex - 1).coerceAtLeast(0)
                selectedKey = rows.getOrNull(next)?.key
            }
            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
            -> {
                val next = (selectedIndex + 1).coerceAtMost(rows.lastIndex.coerceAtLeast(0))
                selectedKey = rows.getOrNull(next)?.key
            }
            AndroidKeyEvent.KEYCODE_DPAD_CENTER,
            AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
            -> {
                if (event.nativeKeyEvent.repeatCount == 0) rows.getOrNull(selectedIndex)?.let(::perform)
            }
        }
        return true
    }

    BackHandler {
        if (legalVisible) legalVisible = false else onClose()
    }

    val settingsKeyHandler = rememberUpdatedState<(KeyEvent) -> Boolean> { event -> handleKey(event) }
    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = { event -> settingsKeyHandler.value(event) }
        KeyRouter.add(priority = 20, handler = handler)
        onDispose { KeyRouter.remove(handler) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LiveColors.Scrim)
            .padding(horizontal = 30.dp, vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.98f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent(::handleKey)
                .focusable(),
            color = LiveColors.Panel,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            shadowElevation = 30.dp,
        ) {
            if (legalVisible) {
                LegalContent(
                    scrollModifier = Modifier.verticalScroll(legalScroll),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 26.dp, vertical = 18.dp),
                ) {
                    SettingsHeader(
                        channelCount = catalog.channels.size,
                        favoritesCount = favoritesCount,
                        epgUpdatedAt = epgUpdatedAt,
                        busy = refreshing || catalogSyncing || epgSyncing,
                    )
                    if (catalog.errors.isNotEmpty() || !epgError.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        SettingsWarnings(
                            sourceErrors = catalog.errors.map { "${it.sourceName}: ${it.message}" },
                            epgError = epgError,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(rows, key = { it.key }) { row ->
                            val selected = row.key == selectedKey
                            Column {
                                row.section?.let { section ->
                                    Text(
                                        text = section,
                                        color = LiveColors.TextDim,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.2.sp,
                                        modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp),
                                    )
                                }
                                SettingsRowItem(row = row, selected = selected)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "▲▼ chọn · OK thay đổi · BACK đóng",
                        color = LiveColors.TextDim,
                        fontSize = 10.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    channelCount: Int,
    favoritesCount: Int,
    epgUpdatedAt: Long,
    busy: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "CÀI ĐẶT",
                color = LiveColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = "$channelCount kênh · $favoritesCount yêu thích · EPG ${formatDateTime(epgUpdatedAt)}",
                color = LiveColors.TextMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = LiveColors.Live,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Đang đồng bộ",
                color = LiveColors.TextMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun SettingsWarnings(sourceErrors: List<String>, epgError: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(LiveColors.Focus.copy(alpha = 0.08f))
            .border(1.dp, LiveColors.Focus.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        sourceErrors.take(2).forEach {
            Text(
                text = it,
                color = LiveColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!epgError.isNullOrBlank()) {
            Text(
                text = "EPG: $epgError",
                color = LiveColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsRowItem(row: SettingsRow, selected: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.075f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) LiveColors.Focus.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.045f),
                shape,
            )
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected) LiveColors.Focus else Color.Transparent),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                color = if (selected) LiveColors.Text else LiveColors.TextMuted,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.subtitle,
                color = LiveColors.TextDim,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        row.value?.let { value ->
            Spacer(Modifier.width(12.dp))
            val valueShape = RoundedCornerShape(6.dp)
            Text(
                text = value,
                color = if (row.valueActive) LiveColors.Good else LiveColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(valueShape)
                    .background(
                        if (row.valueActive) LiveColors.Good.copy(alpha = 0.1f)
                        else Color.White.copy(alpha = 0.05f),
                    )
                    .border(
                        1.dp,
                        if (row.valueActive) LiveColors.Good.copy(alpha = 0.25f)
                        else Color.White.copy(alpha = 0.08f),
                        valueShape,
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LegalContent(
    scrollModifier: Modifier,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp, vertical = 22.dp),
    ) {
        Text(
            text = "GIỚI THIỆU & PHÁP LÝ",
            color = LiveColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
        )
        Spacer(Modifier.height(14.dp))
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().then(scrollModifier),
        ) {
            LegalText(
                "LiveTV là ứng dụng phát các luồng truyền hình công khai do người dùng hoặc cộng đồng cung cấp. " +
                    "Ứng dụng không lưu trữ, không sở hữu và không bán nội dung. Bản quyền nội dung thuộc về đài truyền hình và chủ sở hữu tương ứng.",
            )
            LegalText(
                "Các nguồn danh sách mặc định tổng hợp từ iptv-org, Free-TV/IPTV, All-In-One-IPTV và các luồng công khai của VTV Go. " +
                    "Lịch phát sóng từ VNEPG (epg.io.vn) và EPGShare. Các địa chỉ này có thể thay đổi, chặn theo vùng hoặc ngừng hoạt động mà không báo trước.",
            )
            LegalText(
                "Người dùng chỉ nên xem những kênh mình có quyền truy cập hợp pháp. Một số luồng có thể yêu cầu bản quyền, gói thuê bao hoặc bị giới hạn theo quốc gia. " +
                    "Không sử dụng ứng dụng để chia sẻ lại nội dung trái phép.",
            )
            LegalText(
                "Ứng dụng tự động chuyển sang nguồn dự phòng khi luồng hiện tại lỗi. Chất lượng hình ảnh phụ thuộc vào luồng gốc. " +
                    "Nhấn OK hoặc BACK để đóng.",
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "iptv-org.github.io · github.com/Free-TV/IPTV · github.com/khanh71/All-In-One-IPTV · vtvgo.vn · epg.io.vn · epgshare01.online",
                color = LiveColors.TextDim,
                fontSize = 9.sp,
                lineHeight = 14.sp,
            )
        }
    }
}

@Composable
private fun LegalText(text: String) {
    Text(
        text = text,
        color = LiveColors.TextMuted,
        fontSize = 12.sp,
        lineHeight = 19.sp,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

private fun QualityMode.label(): String = when (this) {
    QualityMode.AUTO -> "Tự động"
    QualityMode.HIGH -> "Chất lượng cao"
    QualityMode.SAVER -> "Tiết kiệm dữ liệu"
}

private fun nextQuality(mode: QualityMode): QualityMode = when (mode) {
    QualityMode.AUTO -> QualityMode.HIGH
    QualityMode.HIGH -> QualityMode.SAVER
    QualityMode.SAVER -> QualityMode.AUTO
}
