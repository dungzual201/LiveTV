package com.livetv.vn.ui

import androidx.compose.ui.input.key.KeyEvent

object KeyRouter {

    private data class Entry(
        val priority: Int,
        val handler: (KeyEvent) -> Boolean,
    )

    private val entries = mutableListOf<Entry>()

    @Synchronized
    fun add(priority: Int, handler: (KeyEvent) -> Boolean) {
        entries.removeAll { it.handler === handler }
        entries += Entry(priority, handler)
        entries.sortByDescending { it.priority }
    }

    @Synchronized
    fun remove(handler: (KeyEvent) -> Boolean) {
        entries.removeAll { it.handler === handler }
    }

    @Synchronized
    fun dispatch(event: KeyEvent): Boolean {
        for (entry in entries.toList()) {
            if (entry.handler(event)) return true
        }
        return false
    }
}
