package com.darkaxt.dualdex.retroarch

sealed interface RetroArchStatus {
    data object Contentless : RetroArchStatus
    data class Running(
        val paused: Boolean,
        val systemId: String,
        val gameBasename: String,
        val crc32: String?,
    ) : RetroArchStatus
    data class Malformed(val raw: String, val reason: String) : RetroArchStatus
}

object RetroArchStatusParser {
    fun parse(raw: String): RetroArchStatus {
        val value = raw.trim().trimEnd('\u0000')
        if (value == "GET_STATUS CONTENTLESS") return RetroArchStatus.Contentless
        val paused = when {
            value.startsWith("GET_STATUS PLAYING ") -> false
            value.startsWith("GET_STATUS PAUSED ") -> true
            else -> return RetroArchStatus.Malformed(value, "unknown GET_STATUS response")
        }
        val payload = value.substringAfter(if (paused) "GET_STATUS PAUSED " else "GET_STATUS PLAYING ")
        val firstSeparator = payload.indexOf(',')
        val crcSeparator = payload.lastIndexOf(",crc32=", ignoreCase = true)
        if (firstSeparator <= 0) {
            return RetroArchStatus.Malformed(value, "content response fields are incomplete")
        }
        val system = payload.substring(0, firstSeparator).trim()
        val gameEnd = crcSeparator.takeIf { it > firstSeparator } ?: payload.length
        val game = payload.substring(firstSeparator + 1, gameEnd).trim()
        val rawCrc = crcSeparator.takeIf { it > firstSeparator }
            ?.let { payload.substring(it + 7).trim().uppercase() }
        if (system.isEmpty() || game.isEmpty() || (rawCrc != null && !rawCrc.matches(Regex("[0-9A-F]{8}")))) {
            return RetroArchStatus.Malformed(value, "content response fields are invalid")
        }
        return RetroArchStatus.Running(paused, system, game, rawCrc?.takeUnless { it == "00000000" })
    }
}
