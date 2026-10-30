package com.livetv.vn.data.guide

import com.livetv.vn.data.model.Program

object ProgramGuide {

    fun current(programs: List<Program>?, at: Long): Program? {
        if (programs.isNullOrEmpty()) return null
        var low = 0
        var high = programs.lastIndex
        var candidate = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (programs[mid].startMs <= at) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (candidate < 0) return null
        val item = programs[candidate]
        return item.takeIf { it.endMs > at }
    }

    fun next(programs: List<Program>?, at: Long): Program? {
        if (programs.isNullOrEmpty()) return null
        var low = 0
        var high = programs.lastIndex
        var candidate: Program? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (programs[mid].startMs > at) {
                candidate = programs[mid]
                high = mid - 1
            } else {
                low = mid + 1
            }
        }
        return candidate
    }

    fun overlapping(programs: List<Program>?, from: Long, to: Long): List<Program> {
        if (programs.isNullOrEmpty() || to <= from) return emptyList()
        var low = 0
        var high = programs.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (programs[mid].startMs < from) low = mid + 1 else high = mid
        }
        var index = low
        while (index > 0 && programs[index - 1].endMs > from) index--
        val result = ArrayList<Program>(4)
        while (index < programs.size && programs[index].startMs < to) {
            if (programs[index].endMs > from) result += programs[index]
            index++
        }
        return result
    }
}
