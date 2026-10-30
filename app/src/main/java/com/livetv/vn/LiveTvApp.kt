package com.livetv.vn

import android.app.Application

class LiveTvApp : Application() {
    val graph: AppGraph by lazy { AppGraph(this) }
}
