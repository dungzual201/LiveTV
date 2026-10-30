package com.livetv.vn.ui.guide

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.Offset
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
import com.livetv.vn.data.guide.ProgramGuide
import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.model.Program
import com.livetv.vn.ui.KeyRouter
import com.livetv.vn.ui.channelNumber
import com.livetv.vn.ui.components.ChannelLogo
import com.livetv.vn.ui.formatClock
import com.livetv.vn.ui.formatDate
import com.livetv.vn.ui.formatDateShort
import com.livetv.vn.ui.formatRange
import com.livetv.vn.ui.theme.LiveColors
import kotlinx.coroutines.flow.first

private const val WINDOW_MINUTES = 180
private const val HALF_HOUR_MS = 30 * 60 * 1000L
private const val ROW_HEIGHT_DP = 56
private const val RAIL_WIDTH_DP = 196

private enum class GuideFilter(val label: String) {
    VIETNAM("Việt Nam"),
    INTERNATIONAL("Quốc tế"),
    HIGH_QUALITY("Chất lượng cao"),
    FAVORITES("Yêu thích"),
    ALL("Tất cả"),
}

private fun filteredChannelsFor(
    channels: List<Channel>,
    favorites: Set<String>,
    filter: GuideFilter,
): List<Channel> = when (filter) {
    GuideFilter.VIETNAM -> channels.filter { it.isVietnamese }
    GuideFilter.INTERNATIONAL -> channels.filterNot { it.isVietnamese }
    GuideFilter.HIGH_QUALITY -> channels
        .filter { channel -> channel.streams.any { it.qualityScore >= 3 } }
        .sortedWith(
            compareBy<Channel> { channel ->
                if (channel.streams.any { it.qualityScore >= 4 }) 0 else 1
            }
                .thenBy { if (it.isVietnamese) 0 else 1 }
                .thenByDescending { channel ->
                    channel.streams.maxOfOrNull { it.qualityScore } ?: 0
                }
                .thenBy { it.number },
        )
    GuideFilter.FAVORITES -> channels.filter { favorites.contains(it.id) }
    GuideFilter.ALL -> channels
}

private enum class GuideZone { LIST, TABS }

@Composable
fun GuideOverlay(
    channels: List<Channel>,
    currentChannelId: String?,
    programsByChannel: Map<String, List<Program>>,
    favorites: Set<String>,
    now: Long,
    epgLoading: Boolean,
    onClose: () -> Unit,
    onTune: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val filters = GuideFilter.entries
    val initialFilter = remember {
        val current = channels.firstOrNull { it.id == currentChannelId }
        when {
            current == null -> GuideFilter.ALL
            current.isVietnamese -> GuideFilter.VIETNAM
            else -> GuideFilter.INTERNATIONAL
        }
    }
    var filter by remember { mutableStateOf(initialFilter) }
    var tabIndex by remember { mutableIntStateOf(filters.indexOf(initialFilter)) }
    var zone by remember { mutableStateOf(GuideZone.LIST) }
    var shiftSteps by remember { mutableIntStateOf(0) }
    var selectedId by remember {
        mutableStateOf(
            channels.firstOrNull { it.id == currentChannelId }?.id,
        )
    }
    val initialChannels = remember(channels, favorites) {
        filteredChannelsFor(channels, favorites, initialFilter)
    }
    val initialSelectedIndex = remember(initialChannels, currentChannelId) {
        initialChannels.indexOfFirst { it.id == currentChannelId }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (initialSelectedIndex - 1).coerceAtLeast(0),
    )
    val focusRequester = remember { FocusRequester() }
    val anchor = remember {
        val thirtyMinutes = 30 * 60 * 1000L
        ((now - thirtyMinutes) / thirtyMinutes) * thirtyMinutes
    }
    val windowStart = anchor + shiftSteps * HALF_HOUR_MS
    val windowEnd = windowStart + WINDOW_MINUTES * 60 * 1000L

    val filteredChannels = remember(channels, favorites, filter, initialChannels) {
        if (filter == initialFilter) initialChannels else filteredChannelsFor(channels, favorites, filter)
    }

    fun applyFilter(newFilter: GuideFilter) {
        filter = newFilter
        tabIndex = filters.indexOf(newFilter)
        val nextChannels = filteredChannelsFor(channels, favorites, newFilter)
        selectedId = if (newFilter == GuideFilter.HIGH_QUALITY) {
            nextChannels.firstOrNull()?.id
        } else {
            currentChannelId?.takeIf { id -> nextChannels.any { it.id == id } }
                ?: nextChannels.firstOrNull()?.id
        }
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(filteredChannels) {
        if (filteredChannels.isEmpty()) {
            selectedId = null
        } else if (filteredChannels.none { it.id == selectedId }) {
            selectedId = currentChannelId?.takeIf { id -> filteredChannels.any { it.id == id } }
                ?.let { id -> filteredChannels.first { it.id == id } }
                ?.id
                ?: filteredChannels.first().id
        }
    }
    val selectedIndex = filteredChannels.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    LaunchedEffect(filter, selectedId) {
        if (zone == GuideZone.LIST && filteredChannels.isNotEmpty()) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
            val visible = listState.layoutInfo.visibleItemsInfo
            val inView = visible.isNotEmpty() &&
                selectedIndex >= visible.first().index && selectedIndex <= visible.last().index
            if (!inView) {
                listState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))
            }
        }
    }

    fun selectTab(index: Int) {
        tabIndex = index.coerceIn(0, filters.size + 1)
        if (tabIndex < filters.size) {
            applyFilter(filters[tabIndex])
        }
    }

    fun openTabs() {
        zone = GuideZone.TABS
        tabIndex = filters.indexOf(filter)
    }

    fun handleKey(event: KeyEvent): Boolean {
        val code = event.nativeKeyEvent.keyCode
        val isConfirm = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            code == AndroidKeyEvent.KEYCODE_ENTER ||
            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        val relevant = code == AndroidKeyEvent.KEYCODE_DPAD_UP ||
            code == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
            code == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
            code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
            isConfirm ||
            code == AndroidKeyEvent.KEYCODE_MENU ||
            code == AndroidKeyEvent.KEYCODE_INFO ||
            code == AndroidKeyEvent.KEYCODE_CHANNEL_UP ||
            code == AndroidKeyEvent.KEYCODE_CHANNEL_DOWN
        if (!relevant) return false

        if (isConfirm) {
            if (event.type == KeyEventType.KeyDown &&
                event.nativeKeyEvent.repeatCount == 0
            ) {
                if (zone == GuideZone.LIST) {
                    selectedId?.let(onTune)
                    return true
                }
                when (tabIndex) {
                    filters.size -> onOpenSettings()
                    filters.size + 1 -> {
                        selectedId?.let(onToggleFavorite)
                        zone = GuideZone.LIST
                    }
                    else -> {
                        applyFilter(filters[tabIndex])
                        zone = GuideZone.LIST
                    }
                }
            }
            return true
        }

        if (event.type != KeyEventType.KeyDown) return true

        if (zone == GuideZone.TABS) {
            val tabCount = filters.size + 2
            when (code) {
                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                    tabIndex = (tabIndex - 1 + tabCount) % tabCount
                    if (tabIndex < filters.size) applyFilter(filters[tabIndex])
                }
                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                    tabIndex = (tabIndex + 1) % tabCount
                    if (tabIndex < filters.size) applyFilter(filters[tabIndex])
                }
                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> zone = GuideZone.LIST
            }
            return true
        }

        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP,
            AndroidKeyEvent.KEYCODE_CHANNEL_UP,
            -> {
                val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                if (selectedIndex <= firstVisible) {
                    openTabs()
                } else {
                    selectedId = filteredChannels[(selectedIndex - 1).coerceAtLeast(0)].id
                }
            }

            AndroidKeyEvent.KEYCODE_DPAD_DOWN,
            AndroidKeyEvent.KEYCODE_CHANNEL_DOWN,
            -> {
                if (filteredChannels.isNotEmpty() && selectedIndex < filteredChannels.lastIndex) {
                    selectedId = filteredChannels[selectedIndex + 1].id
                }
            }

            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                shiftSteps = (shiftSteps - 1).coerceAtLeast(-12)
            }

            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                shiftSteps = (shiftSteps + 1).coerceAtMost(192)
            }

            AndroidKeyEvent.KEYCODE_MENU,
            AndroidKeyEvent.KEYCODE_INFO,
            -> {
                selectedId?.let(onToggleFavorite)
            }
        }
        return true
    }

    BackHandler {
        if (zone == GuideZone.TABS) onClose() else openTabs()
    }

    val guideKeyHandler = rememberUpdatedState<(KeyEvent) -> Boolean> { event -> handleKey(event) }
    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = { event -> guideKeyHandler.value(event) }
        KeyRouter.add(priority = 10, handler = handler)
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
                .fillMaxWidth(0.98f)
                .fillMaxHeight(0.98f)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent(::handleKey)
                .focusable(),
            color = LiveColors.Panel,
            shape = RoundedCornerShape(26.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 16.dp),
            ) {
                GuideHeader(
                    now = now,
                    filters = filters,
                    tabIndex = tabIndex,
                    tabFocused = zone == GuideZone.TABS,
                    onTabClick = { index ->
                        selectTab(index)
                        if (index >= filters.size) onOpenSettings() else zone = GuideZone.LIST
                    },
                )
                Spacer(Modifier.height(10.dp))
                TimelineHeader(windowStart = windowStart)
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black.copy(alpha = 0.16f)),
                ) {
                    if (filteredChannels.isEmpty()) {
                        EmptyGuide(loading = epgLoading && filter != GuideFilter.FAVORITES)
                    } else {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(
                                items = filteredChannels,
                                key = { it.id },
                                contentType = { "guide-row" },
                            ) { channel ->
                                val selected = channel.id == selectedId
                                Column {
                                    GuideRow(
                                        channel = channel,
                                        selected = selected,
                                        isCurrent = channel.id == currentChannelId,
                                        favorite = favorites.contains(channel.id),
                                        programs = programsByChannel[channel.id],
                                        windowStart = windowStart,
                                        windowEnd = windowEnd,
                                        now = now,
                                        epgLoading = epgLoading,
                                    )
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(Color.White.copy(alpha = 0.035f)),
                                    )
                                }
                            }
                        }
                    }
                    if (zone == GuideZone.TABS && tabIndex >= filters.size) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Cài đặt",
                                    color = LiveColors.Text,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "Nhấn OK để mở nguồn, EPG và chất lượng phát",
                                    color = LiveColors.TextMuted,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                GuideFooter(
                    channel = filteredChannels.firstOrNull { it.id == selectedId },
                    programs = filteredChannels.firstOrNull { it.id == selectedId }
                        ?.let { programsByChannel[it.id] },
                    favorite = selectedId != null && favorites.contains(selectedId),
                    now = now,
                    epgLoading = epgLoading,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (zone == GuideZone.TABS) {
                        "◀▶ chọn mục · OK mở · BACK đóng"
                    } else {
                        "▲▼ kênh · OK xem · ◀▶ cuộn giờ · BACK danh mục/⚙"
                    },
                    color = LiveColors.TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GuideHeader(
    now: Long,
    filters: List<GuideFilter>,
    tabIndex: Int,
    tabFocused: Boolean,
    onTabClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.widthIn(min = 230.dp)) {
            Text(
                text = "LỊCH PHÁT SÓNG",
                color = LiveColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp,
            )
            Text(
                text = "${formatDate(now)} · ${formatClock(now)}",
                color = LiveColors.TextMuted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            filters.forEachIndexed { index, item ->
                GuideChip(
                    label = item.label,
                    selected = index == tabIndex,
                    tabFocused = tabFocused,
                )
            }
            GuideChip(
                label = "Cài đặt",
                selected = tabIndex == filters.size,
                tabFocused = tabFocused,
            )
            GuideChip(
                label = "★ Kênh",
                selected = tabIndex == filters.size + 1,
                tabFocused = tabFocused,
            )
        }
    }
}

@Composable
private fun GuideChip(
    label: String,
    selected: Boolean,
    tabFocused: Boolean,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    if (tabFocused) LiveColors.Text else Color.White.copy(alpha = 0.12f)
                } else {
                    Color.Transparent
                },
            )
            .border(
                1.dp,
                if (selected) LiveColors.Text.copy(alpha = if (tabFocused) 1f else 0.2f) else Color.White.copy(alpha = 0.07f),
                shape,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected && tabFocused) LiveColors.Background else LiveColors.TextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun TimelineHeader(windowStart: Long) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Row(
            modifier = Modifier.width(RAIL_WIDTH_DP.dp).padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = "KÊNH",
                color = LiveColors.TextDim,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = formatDateShort(windowStart),
                color = LiveColors.TextDim,
                fontSize = 10.sp,
            )
        }
        Row(modifier = Modifier.weight(1f)) {
            repeat(WINDOW_MINUTES / 30) { index ->
                Text(
                    text = formatClock(windowStart + index * HALF_HOUR_MS),
                    color = LiveColors.TextDim,
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    selected: Boolean,
    isCurrent: Boolean,
    favorite: Boolean,
    programs: List<Program>?,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    epgLoading: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT_DP.dp)
            .background(if (selected) Color.White.copy(alpha = 0.075f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (selected) LiveColors.Live else Color.Transparent),
        )
        Row(
            modifier = Modifier
                .width((RAIL_WIDTH_DP - 3).dp)
                .fillMaxHeight()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = channelNumber(channel.number),
                color = if (isCurrent) LiveColors.Live else if (selected) LiveColors.TextMuted else LiveColors.TextDim,
                fontSize = 10.sp,
                modifier = Modifier.width(25.dp),
            )
            ChannelLogo(channel = channel, size = 30.dp)
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(4.dp)
                        .background(LiveColors.Live, RoundedCornerShape(2.dp)),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = if (selected) LiveColors.Text else LiveColors.TextMuted,
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val quality = channel.bestQualityLabel
            if (quality == "4K" || quality == "FHD") {
                Spacer(Modifier.width(3.dp))
                Text(
                    text = quality,
                    color = LiveColors.Focus,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .border(1.dp, LiveColors.Focus.copy(alpha = 0.45f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                )
            }
            if (favorite) {
                Spacer(Modifier.width(3.dp))
                Text(text = "★", color = LiveColors.Focus, fontSize = 10.sp)
            }
        }
        ProgramTimeline(
            programs = programs,
            windowStart = windowStart,
            windowEnd = windowEnd,
            now = now,
            selected = selected,
            epgLoading = epgLoading,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
    }
}

@Composable
private fun ProgramTimeline(
    programs: List<Program>?,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    selected: Boolean,
    epgLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    if (programs.isNullOrEmpty()) {
        Box(modifier = modifier.clipToBounds(), contentAlignment = Alignment.CenterStart) {
            Text(
                text = if (epgLoading) "Đang tải lịch..." else "Chưa có lịch phát sóng",
                color = LiveColors.TextDim,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        return
    }
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val totalMinutes = WINDOW_MINUTES.toFloat()
        val minuteWidth = maxWidth / totalMinutes
        val overlapping = remember(programs, windowStart, windowEnd) {
            ProgramGuide.overlapping(programs, windowStart, windowEnd)
        }
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val step = size.width / (WINDOW_MINUTES / 30)
            var x = step
            repeat(WINDOW_MINUTES / 30 - 1) {
                drawLine(
                    color = Color.White.copy(alpha = 0.035f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f,
                )
                x += step
            }
        }
        overlapping.forEach { program ->
            val startMinutes = ((program.startMs - windowStart) / 60_000f).coerceIn(0f, totalMinutes)
            val endMinutes = ((program.endMs - windowStart) / 60_000f).coerceIn(0f, totalMinutes)
            val widthMinutes = (endMinutes - startMinutes).coerceAtLeast(1.5f)
            val airing = now in program.startMs until program.endMs
            Box(
                modifier = Modifier
                    .offset(x = minuteWidth * startMinutes)
                    .padding(vertical = 7.dp, horizontal = 1.dp)
                    .width(minuteWidth * widthMinutes)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when {
                            airing -> LiveColors.Live.copy(alpha = if (selected) 0.22f else 0.14f)
                            selected -> Color.White.copy(alpha = 0.10f)
                            else -> Color.White.copy(alpha = 0.055f)
                        },
                    )
                    .padding(horizontal = 7.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = program.title,
                    color = when {
                        airing -> LiveColors.Text
                        program.endMs <= now -> LiveColors.TextDim
                        else -> LiveColors.TextMuted
                    },
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (now >= windowStart && now <= windowEnd) {
            Box(
                modifier = Modifier
                    .offset(x = minuteWidth * ((now - windowStart) / 60_000f))
                    .width(1.5.dp)
                    .fillMaxHeight()
                    .background(LiveColors.Live.copy(alpha = 0.8f)),
            )
        }
        if (overlapping.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = if (epgLoading) "Đang tải lịch..." else "Chưa có lịch phát sóng",
                    color = LiveColors.TextDim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideFooter(
    channel: Channel?,
    programs: List<Program>?,
    favorite: Boolean,
    now: Long,
    epgLoading: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(54.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (channel == null) {
            Text(
                text = if (epgLoading) "Đang tải lịch phát sóng..." else "Không có kênh trong mục này",
                color = LiveColors.TextMuted,
                fontSize = 12.sp,
            )
            return@Row
        }
        val current = ProgramGuide.current(programs, now)
        val next = ProgramGuide.next(programs, now)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = current?.let { "${formatRange(it.startMs, it.endMs)} · ${it.title}" }
                    ?: if (epgLoading) "Đang tải lịch phát sóng..." else "Chưa có lịch phát sóng",
                color = LiveColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = current?.description?.takeIf { it.isNotBlank() }
                    ?: next?.let { "Tiếp: ${formatClock(it.startMs)} · ${it.title}" }
                    ?: "${channel.streams.size} nguồn phát · Chọn OK để xem",
                color = LiveColors.TextDim,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (favorite) {
            Text(text = "★", color = LiveColors.Focus, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = "${channel.streams.size} nguồn",
            color = LiveColors.TextDim,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun EmptyGuide(loading: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = LiveColors.Live,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.height(10.dp))
            }
            Text(
                text = if (loading) "Đang tải lịch phát sóng..." else "Không có kênh trong mục này",
                color = LiveColors.TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
