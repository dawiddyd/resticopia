package org.dydlakcloud.resticopia.ui

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Formatters {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val dateTimeShortFormatter = DateTimeFormatter.ofPattern("yy-MM-dd HH:mm:ss")
    private val dateTimeDetailedFormatter = DateTimeFormatter.ofPattern("HH:mm MMM dd, yyyy").withZone(ZoneId.systemDefault())
    private val dateTimeStatusFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
    private val dateFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy").withZone(ZoneId.systemDefault())

    fun dateTime(dateTime: ZonedDateTime): String =
        dateTime.withZoneSameInstant(ZoneId.systemDefault()).format(dateTimeFormatter)

    fun dateTimeShort(dateTime: ZonedDateTime): String =
        dateTime.withZoneSameInstant(ZoneId.systemDefault()).format(dateTimeShortFormatter)

    fun dateTimeDetailed(dateTime: ZonedDateTime): String =
        dateTime.format(dateTimeDetailedFormatter)

    fun dateTimeStatus(dateTime: ZonedDateTime): String =
        dateTime.format(dateTimeStatusFormatter)

    fun date(dateTime: ZonedDateTime): String =
        dateTime.format(dateFormatter)

    fun durationDaysHours(duration: Duration) = when {
        duration.toHours() < 24 -> "${duration.toHours()} hours"
        duration.toHours() % 24 <= 0 -> "${duration.toHours() / 24} days"
        else -> "${duration.toHours() / 24} days ${duration.toHours()} hours"
    }
}
