package com.livetv.vn

import com.livetv.vn.data.source.M3uParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {

    private val parser = M3uParser()

    @Test
    fun `parses attributes headers and relative urls`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="VTV1.vn@HD" tvg-logo="https://img/vtv1.png" tvg-country="VN" group-title="News",VTV1 HD (1080p)
            #EXTVLCOPT:http-referrer=https://example.com/
            #EXTVLCOPT:http-user-agent=MyPlayer/2.0
            stream/vtv1.m3u8
            #EXTINF:-1 tvg-id="BBCNews.uk",BBC News HD, International
            https://cdn.example.com/bbc.m3u8|Referer=https://ref.example/
        """.trimIndent()

        val entries = parser.parse(content, "https://lists.example.com/vn/list.m3u")
        assertEquals(2, entries.size)

        val vtv1 = entries[0]
        assertEquals("VTV1 HD (1080p)", vtv1.name)
        assertEquals("VTV1.vn@HD", vtv1.tvgId)
        assertEquals("https://lists.example.com/vn/stream/vtv1.m3u8", vtv1.url)
        assertEquals("https://example.com/", vtv1.headers["Referer"])
        assertEquals("MyPlayer/2.0", vtv1.headers["User-Agent"])
        assertEquals(3, vtv1.qualityScore)

        val bbc = entries[1]
        assertEquals("BBC News HD, International", bbc.name)
        assertEquals("https://cdn.example.com/bbc.m3u8", bbc.url)
        assertEquals("https://ref.example/", bbc.headers["Referer"])
    }

    @Test
    fun `parses frame rate hints from entry names`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="VTV1.vn@HD",VTV1 (1080p50)
            https://example.com/vtv1-50.m3u8
            #EXTINF:-1,VTV3 60fps
            https://example.com/vtv3-60.m3u8
        """.trimIndent()
        val entries = parser.parse(content, "https://lists.example.com/list.m3u")
        assertEquals(50, entries[0].fps)
        assertEquals(60, entries[1].fps)
    }

    @Test
    fun `filters unsupported stream providers`() {
        val content = """
            #EXTM3U
            #EXTINF:-1,VTV1
            https://www.youtube.com/watch?v=abc
            #EXTINF:-1,Valid
            https://example.com/live.m3u8
        """.trimIndent()
        val entries = parser.parse(content, "https://lists.example.com/list.m3u")
        assertEquals(1, entries.size)
        assertEquals("Valid", entries.single().name)
    }

    @Test
    fun `skips empty and malformed entries without failing`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="X" group-title="Test",X
            #EXTVLCOPT:http-origin=https://origin.example/
            https://example.com/x.m3u8
            #EXTINF:-1,No URL
            #EXTINF:-1,Another
            ftp://example.com/nope
        """.trimIndent()
        val entries = parser.parse(content, "https://lists.example.com/list.m3u")
        assertEquals(1, entries.size)
        assertTrue(entries.single().headers.containsKey("Origin"))
    }
}
