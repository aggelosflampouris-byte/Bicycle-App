package com.fitnessapp.tracker.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Thread-safe date and time formatting helpers backed by java.time.format.DateTimeFormatter
 * (natively supported on minSdk >= 26 without SimpleDateFormat synchronization hazards).
 */
object DateFormats {

    private val zoneId: ZoneId
        get() = ZoneId.systemDefault()

    // Keep base formatters locale-agnostic and apply Locale.getDefault() dynamically per call
    // to honor runtime locale switches cleanly without [ConstantLocale] issues.
    private val sessionCardPattern = DateTimeFormatter.ofPattern("EEE, MMM d • HH:mm")
    private val fullDatePattern = DateTimeFormatter.ofPattern("EEEE, MMM dd yyyy")
    private val shortDatePattern = DateTimeFormatter.ofPattern("MMM dd, yyyy")
    private val monthDayTimePattern = DateTimeFormatter.ofPattern("MMM d, HH:mm")
    private val fileTimestampPattern = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    private val isoUtcSecondsFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).withZone(ZoneOffset.UTC)
    private val isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val weatherHourFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH", Locale.US)
    private val timeWithSecondsPattern = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val dayOfWeekShortPattern = DateTimeFormatter.ofPattern("EEE")
    private val monthShortPattern = DateTimeFormatter.ofPattern("MMM")

    private fun formatLocalized(epochMs: Long, baseFormatter: DateTimeFormatter): String =
        Instant.ofEpochMilli(epochMs)
            .atZone(zoneId)
            .format(baseFormatter.withLocale(Locale.getDefault()))

    fun formatSessionCard(epochMs: Long): String =
        formatLocalized(epochMs, sessionCardPattern)

    fun formatFullDate(epochMs: Long): String =
        formatLocalized(epochMs, fullDatePattern)

    fun formatShortDate(epochMs: Long): String =
        formatLocalized(epochMs, shortDatePattern)

    fun formatMonthDayTime(epochMs: Long): String =
        formatLocalized(epochMs, monthDayTimePattern)

    fun formatFileTimestamp(epochMs: Long): String =
        formatLocalized(epochMs, fileTimestampPattern)

    fun formatIsoUtcSeconds(epochMs: Long): String =
        isoUtcSecondsFormatter.format(Instant.ofEpochMilli(epochMs))

    fun formatIsoDate(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate().format(isoDateFormatter)

    fun formatWeatherHourKey(epochMs: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(epochMs).atZone(zoneId).format(weatherHourFormatter)

    fun formatTimeOfDay(epochMs: Long = System.currentTimeMillis()): String =
        formatLocalized(epochMs, timeWithSecondsPattern)

    fun formatShortDayOfWeek(epochMs: Long): String =
        formatLocalized(epochMs, dayOfWeekShortPattern)

    fun formatShortMonth(epochMs: Long): String =
        formatLocalized(epochMs, monthShortPattern)

    fun formatWeekOfYear(epochMs: Long): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zoneId).toLocalDate()
        return date.get(WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()).toString()
    }
}

