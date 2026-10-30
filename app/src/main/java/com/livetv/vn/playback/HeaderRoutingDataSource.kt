package com.livetv.vn.playback

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.util.concurrent.CopyOnWriteArrayList

@OptIn(UnstableApi::class)
class HeaderRoutingDataSource(
    private val headerState: StreamHeaderState,
    private val userAgent: String,
) : DataSource {

    private val listeners = CopyOnWriteArrayList<TransferListener>()
    private var delegate: HttpDataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
    }

    override fun open(dataSpec: DataSpec): Long {
        close()
        val currentHeaders = headerState.headers
        val headers = LinkedHashMap(currentHeaders)
        val hasUserAgent = headers.keys.any { it.equals("User-Agent", ignoreCase = true) }
        if (!hasUserAgent) headers["User-Agent"] = userAgent
        val source = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(headers)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        listeners.forEach(source::addTransferListener)
        delegate = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val source = delegate ?: error("DataSource chưa được mở")
        return source.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        (delegate as? HttpDataSource)?.responseHeaders ?: emptyMap()

    override fun close() {
        delegate?.close()
        delegate = null
    }

    @OptIn(UnstableApi::class)
    class Factory(
        private val headerState: StreamHeaderState,
        private val userAgent: String,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            HeaderRoutingDataSource(headerState, userAgent)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
