package com.livetv.vn.ui

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent as AndroidKeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.input.key.KeyEvent as ComposeKeyEvent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.livetv.vn.sync.SyncScheduler
import com.livetv.vn.ui.theme.LiveTvTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        viewModel.handleIntent(intent)
        SyncScheduler.schedule(this)
        setContent {
            LiveTvTheme {
                LiveTvRoot(viewModel)
            }
        }
    }

    override fun dispatchKeyEvent(event: AndroidKeyEvent): Boolean {
        if (KeyRouter.dispatch(ComposeKeyEvent(event))) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        viewModel.onHostStart()
    }

    override fun onStop() {
        viewModel.onHostStop()
        super.onStop()
    }
}
