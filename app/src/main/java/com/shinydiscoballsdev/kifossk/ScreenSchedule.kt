package com.shinydiscoballsdev.kifossk

import android.content.Context
import java.util.Calendar

/**
 * Screen schedule for dashboards that should not glow around the clock.
 *
 * - Keep-on periods: inside them the kiosk keeps the screen on. Outside, the
 *   system screen timeout applies as usual, so the screen goes dark when
 *   nobody touches it.
 * - Wake time: the screen turns on by itself (see [ScreenWakeReceiver]).
 * - Dimming: the kiosk window is dimmed inside one daily period.
 *
 * Weekdays (Mon-Fri) and the weekend (Sat-Sun) each have one wake time and
 * two keep-on periods. A period may cross midnight (e.g. 22:00-06:00).
 * Entries are stored as "HH:MM" or "HH:MM-HH:MM" plus an on/off switch, so
 * the times survive switching an entry off.
 */
object ScreenSchedule {

    enum class Day(val prefix: String) {
        WEEKDAY("schedule_weekday"),
        WEEKEND("schedule_weekend"),
    }

    fun wakeKey(day: Day) = "${day.prefix}_wake"
    fun keepOnKey(day: Day, number: Int) = "${day.prefix}_on$number"
    const val DIM_KEY = "schedule_dim"

    /** Defaults for a fresh install: every entry starts switched off. */
    val DEFAULTS = mapOf(
        wakeKey(Day.WEEKDAY) to "07:00",
        keepOnKey(Day.WEEKDAY, 1) to "07:00-09:00",
        keepOnKey(Day.WEEKDAY, 2) to "17:00-22:00",
        wakeKey(Day.WEEKEND) to "09:00",
        keepOnKey(Day.WEEKEND, 1) to "09:00-22:00",
        keepOnKey(Day.WEEKEND, 2) to "22:00-23:00",
        DIM_KEY to "21:00-07:00",
    )

    /** Minutes after midnight; end <= start means the period crosses midnight. */
    data class Period(val start: Int, val end: Int) {
        private val overnight get() = end <= start

        /** Part of the period on the day it starts. */
        fun coversToday(minute: Int) = if (overnight) minute >= start else minute in start until end

        /** Part after midnight of a period that started the day before. */
        fun coversFromYesterday(minute: Int) = overnight && minute < end
    }

    fun parseTime(text: String): Int? {
        val parts = text.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
    }

    fun parsePeriod(text: String): Period? {
        val parts = text.split("-")
        if (parts.size != 2) return null
        val start = parseTime(parts[0]) ?: return null
        val end = parseTime(parts[1]) ?: return null
        return Period(start, end)
    }

    fun formatTime(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** Should the kiosk keep the screen on right now? */
    fun isKeepOn(context: Context, now: Calendar): Boolean {
        val minute = minuteOfDay(now)
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }

        return keepOnPeriods(context, dayOf(now)).any { it.coversToday(minute) } ||
            keepOnPeriods(context, dayOf(yesterday)).any { it.coversFromYesterday(minute) }
    }

    /** Window brightness (0..1) while dimmed, null outside the dimming period. */
    fun dimLevel(context: Context, now: Calendar): Float? {
        val period = entry(context, DIM_KEY)?.let(::parsePeriod) ?: return null
        val minute = minuteOfDay(now)

        return if (period.coversToday(minute) || period.coversFromYesterday(minute)) {
            KioskPrefs.getDimPercent(context) / 100f
        } else {
            null
        }
    }

    /** Next wake time in epoch millis within the coming week, null if none is set. */
    fun nextWake(context: Context, now: Calendar): Long? {
        for (offset in 0..7) {
            val day = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
            val wake = entry(context, wakeKey(dayOf(day)))?.let(::parseTime) ?: continue

            day.set(Calendar.HOUR_OF_DAY, wake / 60)
            day.set(Calendar.MINUTE, wake % 60)
            day.set(Calendar.SECOND, 0)
            day.set(Calendar.MILLISECOND, 0)
            if (day.after(now)) return day.timeInMillis
        }
        return null
    }

    private fun keepOnPeriods(context: Context, day: Day) =
        listOfNotNull(
            entry(context, keepOnKey(day, 1))?.let(::parsePeriod),
            entry(context, keepOnKey(day, 2))?.let(::parsePeriod),
        )

    /** Value of a switched-on entry, null when it is switched off. */
    private fun entry(context: Context, key: String): String? {
        val (enabled, value) = KioskPrefs.getScheduleEntry(context, key, DEFAULTS.getValue(key))
        return if (enabled) value else null
    }

    private fun dayOf(calendar: Calendar) = when (calendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY, Calendar.SUNDAY -> Day.WEEKEND
        else -> Day.WEEKDAY
    }

    private fun minuteOfDay(calendar: Calendar) =
        calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
}
