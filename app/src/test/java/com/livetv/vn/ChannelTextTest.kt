package com.livetv.vn

import com.livetv.vn.data.util.ChannelText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelTextTest {

    @Test
    fun `same channel from different sources merges`() {
        val a = ChannelText.identityId("VN", "VTV1 HD (1080p)")
        val b = ChannelText.identityId("VN", "VTV1")
        assertEquals(a, b)
        assertEquals("vn.vtv1", a)
    }

    @Test
    fun `epg ids normalize across conventions`() {
        assertEquals("vtv1", ChannelText.idKey("VTV1.vn@HD"))
        assertEquals("vtv1", ChannelText.idKey("VTV1.HD.vn"))
        assertEquals("vtv1", ChannelText.idKey("vtv1hd"))
        assertEquals("vtv1", ChannelText.nameKey("VTV1 HD"))
        assertEquals("bbcnews", ChannelText.idKey("BBCNews.uk"))
    }

    @Test
    fun `regional feed ids normalize`() {
        assertEquals("vtv5tnb", ChannelText.idKey("vtv5hdtnb"))
        assertEquals("vtv5taynambo", ChannelText.nameKey("VTV5 Tây Nam Bộ"))
    }

    @Test
    fun `vietnamese aliases unify acronyms`() {
        assertEquals(ChannelText.nameKey("An Ninh TV"), ChannelText.nameKey("ANTV"))
        assertEquals(ChannelText.nameKey("HTV Sports"), ChannelText.nameKey("HTV Thể Thao"))
    }

    @Test
    fun `quality is detected from name and url`() {
        assertEquals(3, ChannelText.quality("VTV1 (1080p)").score)
        assertEquals(1, ChannelText.quality("VTV2", "https://x/playlist_360p.m3u8").score)
        assertEquals("HD", ChannelText.quality("BBC News HD").label)
    }

    @Test
    fun `non latin names remain distinct and usable`() {
        val cyrillic = ChannelText.nameKey("Небеса ТВ7")
        assertTrue(cyrillic.isNotBlank())
        assertTrue(cyrillic != "7")
        val id = ChannelText.identityId("RU", "Небеса ТВ7")
        assertTrue(id.startsWith("ru."))
        assertTrue(id.all { it.code < 128 })
        assertTrue(ChannelText.identityId("RU", "Небеса ТВ7") != ChannelText.identityId("RU", "Небеса ТВ8"))
    }

    @Test
    fun `clean names remove source decorations`() {
        val cleaned = ChannelText.cleanName("VTV1 HD (1080p) [Geo-blocked]")
        assertEquals("VTV1", cleaned)
        assertTrue(ChannelText.cleanName("VTV5 Tây Nam Bộ HD").contains("Tây Nam Bộ"))
    }
}
