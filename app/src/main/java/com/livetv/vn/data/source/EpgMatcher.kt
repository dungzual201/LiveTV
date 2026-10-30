package com.livetv.vn.data.source

import com.livetv.vn.data.model.Channel
import com.livetv.vn.data.util.ChannelText

class EpgMatcher(channels: List<Channel>) {

    private val byCountryTvg = HashMap<String, Channel>()
    private val byCountryName = HashMap<String, Channel>()
    private val byTvg = HashMap<String, Channel>()
    private val byName = HashMap<String, Channel>()

    init {
        channels.forEach { channel ->
            val country = ChannelText.normalizeCountry(channel.country) ?: "XX"
            val tvg = ChannelText.idKey(channel.tvgId)
            val name = ChannelText.nameKey(channel.name)
            if (tvg.isNotBlank()) {
                byCountryTvg.putIfAbsent("$country:$tvg", channel)
                byTvg.putIfAbsent(tvg, channel)
            }
            if (name.isNotBlank()) {
                byCountryName.putIfAbsent("$country:$name", channel)
                byName.putIfAbsent(name, channel)
            }
        }
    }

    fun resolve(providerChannelId: String, displayNames: List<String>, defaultCountry: String?): String? {
        val id = ChannelText.idKey(providerChannelId)
        val countries = buildList {
            ChannelText.countryFromTvgId(providerChannelId)?.let(::add)
            ChannelText.normalizeCountry(defaultCountry)?.let(::add)
        }.distinct()

        if (id.isNotBlank()) {
            countries.forEach { country ->
                byCountryTvg["$country:$id"]?.let { return it.id }
            }
            byTvg[id]?.let { return it.id }
        }

        displayNames.forEach { displayName ->
            val name = ChannelText.nameKey(displayName)
            if (name.isBlank()) return@forEach
            countries.forEach { country ->
                byCountryName["$country:$name"]?.let { return it.id }
            }
            byName[name]?.let { return it.id }
        }
        return null
    }
}
