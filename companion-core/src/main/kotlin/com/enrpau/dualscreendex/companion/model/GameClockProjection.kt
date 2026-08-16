package com.enrpau.dualscreendex.companion.model

fun projectGameClock(
    hours: Int,
    minutes: Int,
    dayStartHour: Int?,
    nightStartHour: Int?,
): GameClock {
    val numeric = GameClock(hours, minutes)
    if (
        dayStartHour == null || nightStartHour == null ||
        dayStartHour !in 0..23 || nightStartHour !in 0..23 ||
        dayStartHour == nightStartHour
    ) return numeric

    val minuteOfDay = hours * MINUTES_PER_HOUR + minutes
    val dayStart = dayStartHour * MINUTES_PER_HOUR
    val nightStart = nightStartHour * MINUTES_PER_HOUR
    val isDay = inForwardInterval(minuteOfDay, dayStart, nightStart)
    val phaseStart = if (isDay) dayStart else nightStart
    val phaseEnd = if (isDay) nightStart else dayStart
    val duration = forwardDistance(phaseStart, phaseEnd)
    val elapsed = forwardDistance(phaseStart, minuteOfDay)
    return GameClock(
        hours = hours,
        minutes = minutes,
        phase = if (isDay) GameClockPhase.DAY else GameClockPhase.NIGHT,
        phaseProgress = elapsed.toDouble() / duration,
    )
}

private fun inForwardInterval(value: Int, start: Int, end: Int): Boolean =
    if (start < end) value in start until end else value >= start || value < end

private fun forwardDistance(start: Int, end: Int): Int =
    (end - start + MINUTES_PER_DAY) % MINUTES_PER_DAY

private const val MINUTES_PER_HOUR = 60
private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
