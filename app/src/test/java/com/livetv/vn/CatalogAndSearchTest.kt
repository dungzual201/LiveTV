package com.livetv.vn

import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.model.M3uEntry
import com.livetv.vn.data.model.PlaylistSource
import com.livetv.vn.data.source.CatalogMerger
import com.livetv.vn.data.source.EpgMatcher
import com.livetv.vn.data.source.PlaylistSnapshot
import com.livetv.vn.data.util.ChannelSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAndSearchTest {

    private val sourceA = PlaylistSource(id = "a", name = "Nguồn A", url = "", priority = 100, defaultCountry = "VN")
    private val sourceB = PlaylistSource(id = "b", name = "Nguồn B", url = "", priority = 50, defaultCountry = "VN")

    @Test
    fun `merges multiple streams for the same Vietnamese channel`() {
        val snapshots = listOf(
            PlaylistSnapshot(
                sourceA,
                listOf(
                    M3uEntry(name = "HTV7 (720p)", url = "https://a/htv7.m3u8", tvgId = "HTV7.vn@SD"),
                    M3uEntry(name = "VTV1 HD (1080p)", url = "https://a/vtv1.m3u8", tvgId = "VTV1.vn@HD"),
                ),
            ),
            PlaylistSnapshot(
                sourceB,
                listOf(
                    M3uEntry(name = "VTV1", url = "https://b/vtv1.m3u8", tvgId = "vtv1hd"),
                    M3uEntry(name = "BBC News HD", url = "https://b/bbc.m3u8", tvgId = "BBCNews.uk", country = "GB"),
                ),
            ),
        )
        val catalog = CatalogMerger.merge(snapshots, now = 1L)
        val vtv1 = catalog.channels.first { it.id == "vn.vtv1" }
        assertEquals(2, vtv1.streams.size)
        assertEquals(1, vtv1.number)
        val htv7 = catalog.channels.first { it.id == "vn.htv7" }
        assertTrue(htv7.number < catalog.channels.first { it.id == "gb.bbcnews" }.number)
    }

    @Test
    fun `epg matcher handles vnepg and epgshare id styles`() {
        val channels = listOf(
            Channel(
                id = "vn.vtv1",
                name = "VTV1",
                tvgId = "VTV1.vn@HD",
                country = "VN",
                number = 1,
            ),
            Channel(
                id = "vn.anninhtv",
                name = "An Ninh TV",
                tvgId = "AnNinhTV.vn@HD",
                country = "VN",
                number = 2,
            ),
        )
        val matcher = EpgMatcher(channels)
        assertEquals("vn.vtv1", matcher.resolve("vtv1hd", emptyList(), "VN"))
        assertEquals("vn.vtv1", matcher.resolve("VTV1.HD.vn", listOf("VTV1 HD"), "VN"))
        assertEquals("vn.anninhtv", matcher.resolve("antvhd", listOf("ANTV"), "VN"))
    }

    @Test
    fun `search understands Vietnamese voice phrases`() {
        val channels = listOf(
            Channel(id = "vn.vtv1", name = "VTV1", country = "VN", number = 1),
            Channel(id = "vn.vtv3", name = "VTV3", country = "VN", number = 3),
            Channel(id = "gb.bbcnews", name = "BBC News", country = "GB", number = 300),
            Channel(id = "vn.htv7", name = "HTV7", country = "VN", number = 25),
            Channel(id = "vn.vtv7", name = "VTV7", country = "VN", number = 9),
            Channel(id = "vn.vtv5taynguyen", name = "VTV5 Tây Nguyên", country = "VN", number = 7),
            Channel(id = "ru.nebesa", name = "Небеса ТВ7", country = "RU", number = 700),
        )
        assertEquals("vn.vtv1", ChannelSearch.find(channels, "mở kênh VTV1")?.id)
        assertEquals("vn.vtv3", ChannelSearch.find(channels, "xem kênh 3")?.id)
        assertEquals("gb.bbcnews", ChannelSearch.find(channels, "watch BBC News")?.id)
        assertNotNull(ChannelSearch.find(channels, "VTV1"))
        assertEquals("vn.htv7", ChannelSearch.find(channels, "HTV7")?.id)
        assertEquals("vn.vtv7", ChannelSearch.find(channels, "VTV7")?.id)
        assertEquals("vn.vtv3", ChannelSearch.find(channels, "mở kênh 3")?.id)
        assertEquals("vn.vtv5taynguyen", ChannelSearch.find(channels, "kênh 7")?.id)
        assertEquals("ru.nebesa", ChannelSearch.find(channels, "Небеса ТВ7")?.id)
    }
}
