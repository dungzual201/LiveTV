package com.livetv.vn.playback

class StreamHeaderState {
    @Volatile
    var headers: Map<String, String> = emptyMap()
}
