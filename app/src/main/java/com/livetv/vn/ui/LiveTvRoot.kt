package com.livetv.vn.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.imageLoader
import coil.request.ImageRequest
import com.livetv.vn.data.guide.ProgramGuide
import com.livetv.vn.playback.PlaybackStatus
import com.livetv.vn.ui.components.BufferingPill
import com.livetv.vn.ui.components.LiveInfoCard
import com.livetv.vn.ui.components.PlaybackErrorOverlay
import com.livetv.vn.ui.components.PlayerSurface
import com.livetv.vn.ui.components.StartupContent
import com.livetv.vn.ui.components.ToastPill
import com.livetv.vn.ui.guide.GuideOverlay
import com.livetv.vn.ui.settings.SettingsOverlay
import com.livetv.vn.ui.theme.LiveColors
import kotlinx.coroutines.delay

@Composable
fun LiveTvRoot(viewModel: MainViewModel) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val programs by viewModel.programs.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    val qualityMode by viewModel.qualityMode.collectAsStateWithLifecycle()
    val initialized by viewModel.initialized.collectAsStateWithLifecycle()
    val catalogSyncing by viewModel.catalogSyncing.collectAsStateWithLifecycle()
    val epgSyncing by viewModel.epgSyncing.collectAsStateWithLifecycle()
    val epgCacheLoading by viewModel.epgCacheLoading.collectAsStateWithLifecycle()
    val epgUpdatedAt by viewModel.epgUpdatedAt.collectAsStateWithLifecycle()
    val epgError by viewModel.epgError.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }

    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var infoVisible by remember { mutableStateOf(false) }
    var infoNonce by remember { mutableIntStateOf(0) }
    var errorAction by remember { mutableIntStateOf(0) }
    val liveFocusRequester = remember { FocusRequester() }

    val currentChannel = playback.channelId?.let { catalog.byId[it] }
    val currentProgram = currentChannel?.let { channel ->
        ProgramGuide.current(programs[channel.id], now)
    }
    val nextProgram = currentChannel?.let { channel ->
        ProgramGuide.next(programs[channel.id], now)
    }
    val isFavorite = currentChannel?.let { favorites.contains(it.id) } == true
    val context = LocalContext.current
    LaunchedEffect(catalog.channels, currentChannel?.id) {
        val channels = catalog.channels
        if (channels.isEmpty()) return@LaunchedEffect
        val currentIndex = channels.indexOfFirst { it.id == currentChannel?.id }
        val targets = buildList {
            if (currentIndex >= 0) {
                for (offset in -2..8) channels.getOrNull(currentIndex + offset)?.let(::add)
            }
            addAll(channels.take(6))
        }.distinctBy { it.id }
        targets.forEach { channel ->
            val logo = channel.logo?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: return@forEach
            context.imageLoader.enqueue(
                ImageRequest.Builder(context)
                    .data(logo)
                    .size(96)
                    .crossfade(false)
                    .build(),
            )
        }
    }

    LaunchedEffect(playback.channelId, infoNonce) {
        if (playback.channelId != null) {
            infoVisible = true
            delay(6_500)
            infoVisible = false
        }
    }
    LaunchedEffect(playback.error) {
        if (playback.isError) errorAction = 0
    }
    LaunchedEffect(showGuide, showSettings, playback.channelId) {
        if (!showGuide && !showSettings) {
            delay(40)
            runCatching { liveFocusRequester.requestFocus() }
        }
    }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        delay(3_200)
        if (viewModel.message.value == current) viewModel.consumeMessage()
    }
    LaunchedEffect(catalog.channels.isEmpty()) {
        if (catalog.channels.isEmpty() && showGuide) showGuide = false
    }

    fun handleLiveKey(event: KeyEvent): Boolean {
        val code = event.nativeKeyEvent.keyCode
        val confirm = code == AndroidKeyEvent.KEYCODE_DPAD_CENTER ||
            code == AndroidKeyEvent.KEYCODE_ENTER ||
            code == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER
        val up = code == AndroidKeyEvent.KEYCODE_DPAD_UP ||
            code == AndroidKeyEvent.KEYCODE_CHANNEL_UP
        val down = code == AndroidKeyEvent.KEYCODE_DPAD_DOWN ||
            code == AndroidKeyEvent.KEYCODE_CHANNEL_DOWN
        val relevant = confirm || up || down ||
            code == AndroidKeyEvent.KEYCODE_DPAD_LEFT ||
            code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT ||
            code == AndroidKeyEvent.KEYCODE_MENU ||
            code == AndroidKeyEvent.KEYCODE_SETTINGS ||
            code == AndroidKeyEvent.KEYCODE_INFO ||
            code == AndroidKeyEvent.KEYCODE_GUIDE
        if (!relevant) return false
        if (event.type != KeyEventType.KeyDown) return true
        if (showGuide || showSettings) return true
        val repeat = event.nativeKeyEvent.repeatCount > 0

        if (catalog.channels.isEmpty()) {
            if (confirm && !repeat && !catalogSyncing && !refreshing) {
                viewModel.refreshEverything()
            }
            if ((code == AndroidKeyEvent.KEYCODE_MENU || code == AndroidKeyEvent.KEYCODE_SETTINGS) && !repeat) {
                showSettings = true
            }
            return true
        }

        when {
            code == AndroidKeyEvent.KEYCODE_MENU || code == AndroidKeyEvent.KEYCODE_SETTINGS -> {
                if (!repeat) {
                    infoVisible = false
                    showSettings = true
                }
            }

            code == AndroidKeyEvent.KEYCODE_INFO -> {
                if (!repeat) infoNonce++
            }

            code == AndroidKeyEvent.KEYCODE_GUIDE -> {
                if (!repeat) {
                    infoVisible = false
                    showGuide = true
                }
            }

            up -> {
                if (!repeat) viewModel.zap(-1)
            }

            down -> {
                if (!repeat) viewModel.zap(1)
            }

            confirm -> {
                if (!repeat) {
                    if (playback.isError) {
                        if (errorAction == 0) {
                            viewModel.retryPlayback()
                        } else {
                            showGuide = true
                        }
                    } else {
                        infoVisible = false
                        showGuide = true
                    }
                }
            }

            code == AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (playback.isError && !repeat) errorAction = 0
            }

            code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (playback.isError && !repeat) errorAction = 1
            }
        }
        return true
    }

    val liveKeyHandler = rememberUpdatedState<(KeyEvent) -> Boolean> { event ->
        if (showGuide || showSettings) false else handleLiveKey(event)
    }
    DisposableEffect(Unit) {
        val handler: (KeyEvent) -> Boolean = { event -> liveKeyHandler.value(event) }
        KeyRouter.add(priority = 0, handler = handler)
        onDispose { KeyRouter.remove(handler) }
    }

    Box(modifier = Modifier.fillMaxSize().background(LiveColorsBackground)) {
            if (catalog.channels.isNotEmpty()) {
                PlayerSurface(
                    player = viewModel.player,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                StartupContent(
                    initialized = initialized,
                    syncing = catalogSyncing || refreshing,
                    errors = catalog.errors.map { "${it.sourceName}: ${it.message}" },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(liveFocusRequester)
                    .onPreviewKeyEvent(::handleLiveKey)
                    .focusable(),
            )

            if (catalog.channels.isNotEmpty()) {
                AnimatedVisibility(
                    visible = infoVisible && !showGuide && !showSettings && !playback.isError,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 }),
                    exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 6 }),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 42.dp, vertical = 34.dp),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        val channel = currentChannel
                        if (channel != null) {
                            LiveInfoCard(
                                channel = channel,
                                snapshot = playback,
                                currentProgram = currentProgram,
                                nextProgram = nextProgram,
                                now = now,
                                isFavorite = isFavorite,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = playback.isBuffering && !showGuide && !showSettings,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 32.dp),
                ) {
                    BufferingPill(snapshot = playback)
                }

                if (playback.isError && !showGuide && !showSettings) {
                    PlaybackErrorOverlay(
                        channel = currentChannel,
                        snapshot = playback,
                        selectedAction = errorAction,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            AnimatedVisibility(
                visible = showGuide && catalog.channels.isNotEmpty(),
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(80)),
                modifier = Modifier.fillMaxSize(),
            ) {
                GuideOverlay(
                    channels = catalog.channels,
                    currentChannelId = playback.channelId,
                    programsByChannel = programs,
                    favorites = favorites,
                    now = now,
                    epgLoading = epgSyncing || epgCacheLoading,
                    onClose = { showGuide = false },
                    onTune = { channelId ->
                        viewModel.playChannel(channelId)
                        showGuide = false
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpenSettings = {
                        showGuide = false
                        showSettings = true
                    },
                )
            }

            AnimatedVisibility(
                visible = showSettings,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                SettingsOverlay(
                    sources = sources,
                    catalog = catalog,
                    favoritesCount = favorites.size,
                    qualityMode = qualityMode,
                    catalogSyncing = catalogSyncing,
                    epgSyncing = epgSyncing,
                    epgUpdatedAt = epgUpdatedAt,
                    epgError = epgError,
                    refreshing = refreshing,
                    onClose = { showSettings = false },
                    onTogglePlaylist = viewModel::setPlaylistEnabled,
                    onToggleEpg = viewModel::setEpgEnabled,
                    onSetQualityMode = viewModel::setQualityMode,
                    onRefresh = viewModel::refreshEverything,
                    onClearCache = viewModel::clearTemporaryData,
                    onResetSources = viewModel::resetSources,
                )
            }

            AnimatedVisibility(
                visible = message != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp),
            ) {
                message?.let { ToastPill(text = it) }
            }
        }
}

private val LiveColorsBackground = LiveColors.Background
