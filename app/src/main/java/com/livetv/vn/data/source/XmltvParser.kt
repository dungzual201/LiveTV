package com.livetv.vn.data.source

import com.livetv.vn.data.model.Program
import java.io.BufferedInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

class XmltvParser(
    private val windowStartMs: Long,
    private val windowEndMs: Long,
    private val resolver: (providerChannelId: String, displayNames: List<String>) -> String?,
    private val onProgram: (Program) -> Unit,
) {
    data class Stats(
        val channelsSeen: Int,
        val programsSeen: Int,
        val programsUsed: Int,
    )

    private val dateWithZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        isLenient = false
    }
    private val dateWithoutZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getDefault()
    }

    private class ChannelInfo(
        val id: String,
        val displayNames: MutableList<String> = mutableListOf(),
    )

    private class ProgramInfo(
        val providerId: String,
        val startRaw: String,
        val stopRaw: String,
    ) {
        var title: String = ""
        var subTitle: String = ""
        var description: String = ""
        var category: String = ""
    }

    fun parse(input: InputStream): Stats {
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        }
        val parser = factory.newSAXParser()
        val handler = Handler()
        parser.parse(decompressIfNeeded(input), handler)
        return Stats(
            channelsSeen = handler.channelCount,
            programsSeen = handler.programsSeen,
            programsUsed = handler.programsUsed,
        )
    }

    private inner class Handler : DefaultHandler() {
        private val providerChannels = HashMap<String, ChannelInfo>()
        private var currentChannel: ChannelInfo? = null
        private var inChannel = false
        private var currentProgram: ProgramInfo? = null
        private var currentTag: String? = null
        private val text = StringBuilder()

        var channelCount = 0
            private set
        var programsSeen = 0
            private set
        var programsUsed = 0
            private set

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            val tag = (localName?.ifBlank { qName } ?: qName ?: return).lowercase(Locale.ROOT)
            when (tag) {
                "channel" -> {
                    val id = attributes?.getValue("id").orEmpty()
                    if (id.isNotBlank()) {
                        currentChannel = ChannelInfo(id)
                        inChannel = true
                    }
                }

                "display-name" -> {
                    if (inChannel) startText()
                }

                "programme" -> {
                    val channel = attributes?.getValue("channel").orEmpty()
                    val start = attributes?.getValue("start").orEmpty()
                    val stop = attributes?.getValue("stop").orEmpty()
                    if (channel.isNotBlank() && start.isNotBlank() && stop.isNotBlank()) {
                        currentProgram = ProgramInfo(channel, start, stop)
                        programsSeen++
                    }
                }

                "title", "sub-title", "desc", "category" -> {
                    if (currentProgram != null) startText()
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (currentTag == null) return
            if (text.length < MAX_TEXT_CHARS) {
                text.append(ch, start, minOf(length, MAX_TEXT_CHARS - text.length))
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            val tag = (localName?.ifBlank { qName } ?: qName ?: return).lowercase(Locale.ROOT)
            when (tag) {
                "display-name" -> {
                    if (inChannel) {
                        endText()?.takeIf { it.isNotBlank() }?.let { currentChannel?.displayNames?.add(it) }
                    } else {
                        clearText()
                    }
                }

                "channel" -> {
                    val info = currentChannel
                    if (info != null) {
                        providerChannels[info.id] = info
                        channelCount++
                    }
                    currentChannel = null
                    inChannel = false
                }

                "title" -> {
                    val value = endText()
                    val program = currentProgram
                    if (value != null && program != null && program.title.isBlank()) program.title = value
                }

                "sub-title" -> {
                    val value = endText()
                    val program = currentProgram
                    if (value != null && program != null && program.subTitle.isBlank()) program.subTitle = value
                }

                "desc" -> {
                    val value = endText()
                    val program = currentProgram
                    if (value != null && program != null && program.description.isBlank()) {
                        program.description = value
                    }
                }

                "category" -> {
                    val value = endText()
                    val program = currentProgram
                    if (value != null && program != null && program.category.isBlank()) program.category = value
                }

                "programme" -> {
                    finishProgram()
                }
            }
        }

        private fun finishProgram() {
            val program = currentProgram ?: return
            currentProgram = null
            currentTag = null
            text.clear()
            val start = parseDate(program.startRaw) ?: return
            val stop = parseDate(program.stopRaw) ?: return
            if (stop <= start) return
            if (stop < windowStartMs || start > windowEndMs) return
            val names = providerChannels[program.providerId]?.displayNames.orEmpty()
            val channelId = resolver(program.providerId, names) ?: return
            val title = program.title.ifBlank { program.subTitle }.trim().take(MAX_TITLE)
            if (title.isBlank()) return
            onProgram(
                Program(
                    channelId = channelId,
                    startMs = start,
                    endMs = stop,
                    title = title,
                    description = program.description.trim().take(MAX_DESCRIPTION),
                    category = program.category.trim().take(MAX_CATEGORY),
                ),
            )
            programsUsed++
        }

        private fun startText() {
            currentTag = "text"
            text.clear()
        }

        private fun endText(): String? {
            if (currentTag == null) return null
            val value = text.toString().trim()
            currentTag = null
            text.clear()
            return value
        }

        private fun clearText() {
            currentTag = null
            text.clear()
        }
    }

    private fun parseDate(raw: String): Long? {
        val value = raw.trim()
        if (value.length < 14) return null
        return runCatching { dateWithZone.parse(value) }.getOrNull()?.time
            ?: runCatching { dateWithoutZone.parse(value) }.getOrNull()?.time
    }

    private fun decompressIfNeeded(input: InputStream): InputStream {
        val buffered = if (input is BufferedInputStream) input else BufferedInputStream(input)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == 0x1f && second == 0x8b) GZIPInputStream(buffered) else buffered
    }

    private companion object {
        const val MAX_TEXT_CHARS = 1200
        const val MAX_TITLE = 180
        const val MAX_DESCRIPTION = 450
        const val MAX_CATEGORY = 60
    }
}
