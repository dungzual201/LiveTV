package com.livetv.vn.tif

import android.app.Activity
import android.media.tv.TvInputInfo
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LiveTvSetupActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply {
            text = "Đang thiết lập LiveTV cho Google Assistant…"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(0xFFF4F6F8.toInt())
            setBackgroundColor(0xFF060708.toInt())
        }
        setContentView(label)
        val inputId = intent?.getStringExtra(TvInputInfo.EXTRA_INPUT_ID)
            ?: TifChannelSync.defaultInputId(this)
        scope.launch {
            TifChannelSync.syncFromIndex(applicationContext, inputId)
            withContext(Dispatchers.Main) {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
}
