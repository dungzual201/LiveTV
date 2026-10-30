package com.livetv.vn

import com.livetv.vn.data.guide.ProgramGuide
import com.livetv.vn.data.model.Program
import com.livetv.vn.data.source.XmltvParser
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class XmltvAndGuideTest {

    @Test
    fun `xmltv parses programmes and resolves channels`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <channel id="VTV1.HD.vn"><display-name>VTV1 HD</display-name></channel>
              <programme start="20261030100000 +0700" stop="20261030103000 +0700" channel="VTV1.HD.vn">
                <title>Bản tin sáng</title>
                <desc>Thời sự trong nước và quốc tế</desc>
                <category>News</category>
              </programme>
              <programme start="20261030103000 +0700" stop="20261030110000 +0700" channel="VTV1.HD.vn">
                <title>Chương trình tiếp theo</title>
              </programme>
            </tv>
        """.trimIndent()
        val parsed = ArrayList<Program>()
        val parser = XmltvParser(
            windowStartMs = 0,
            windowEndMs = Long.MAX_VALUE,
            resolver = { id, names ->
                if (id.contains("VTV1") || names.any { it.contains("VTV1") }) "vn.vtv1" else null
            },
            onProgram = parsed::add,
        )
        val stats = parser.parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(1, stats.channelsSeen)
        assertEquals(2, stats.programsUsed)
        assertEquals(2, parsed.size)
        assertEquals("vn.vtv1", parsed[0].channelId)
        assertEquals("Bản tin sáng", parsed[0].title)
        assertEquals(1800000L, parsed[0].endMs - parsed[0].startMs)
    }

    @Test
    fun `program guide finds current and next`() {
        val base = 1_000_000L
        val programs = listOf(
            Program("c", base, base + 1000, "A"),
            Program("c", base + 1000, base + 2000, "B"),
            Program("c", base + 3000, base + 4000, "D"),
        )
        assertNull(ProgramGuide.current(programs, base - 1))
        assertEquals("A", ProgramGuide.current(programs, base + 10)?.title)
        assertEquals("B", ProgramGuide.current(programs, base + 1500)?.title)
        assertNull(ProgramGuide.current(programs, base + 2500))
        assertEquals("B", ProgramGuide.next(programs, base + 10)?.title)
        assertEquals("D", ProgramGuide.next(programs, base + 2000)?.title)
        assertNull(ProgramGuide.next(programs, base + 3500))
        assertEquals(listOf("B", "D"), ProgramGuide.overlapping(programs, base + 1200, base + 3500).map { it.title })
    }
}
