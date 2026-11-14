package com.example.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object CurrencyFormatter {
    private val indianLocale = Locale("en", "IN")

    fun formatInr(amount: BigDecimal, showDecimalsIfNonZero: Boolean = false): String {
        val absAmount = amount.abs().setScale(2, RoundingMode.HALF_UP)
        val hasFraction = absAmount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0
        val useDecimals = showDecimalsIfNonZero && hasFraction
        val pattern = if (useDecimals) "#,##,##0.00" else "#,##,##0"
        val formatter = DecimalFormat(pattern, DecimalFormatSymbols(indianLocale))
        val formattedNumber = formatter.format(
            if (useDecimals) absAmount else absAmount.setScale(0, RoundingMode.HALF_UP)
        )
        val sign = if (amount < BigDecimal.ZERO) "-" else ""
        return "${sign}₹$formattedNumber"
    }

    fun formatInrExact(amount: BigDecimal): String {
        return formatInr(amount, showDecimalsIfNonZero = true)
    }

    fun formatSignedInr(amount: BigDecimal, isCredit: Boolean): String {
        val prefix = if (isCredit) "+" else ""
        return "$prefix${formatInr(amount, showDecimalsIfNonZero = true)}"
    }
}

object DateUtils {
    private val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val dayMonthFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val fullDateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a", Locale.ENGLISH)
    private val dateOnlyFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)

    fun formatMonthYear(yearMonth: YearMonth): String = yearMonth.format(monthYearFormatter)

    fun formatDateTime(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId)
        return dt.format(fullDateTimeFormatter)
    }

    fun formatDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), zoneId)
        return dt.format(dateOnlyFormatter)
    }

    fun formatDayHeader(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(dayMonthFormatter)
        }
    }

    fun toLocalDate(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate {
        return Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
    }

    fun startOfMonthMillis(yearMonth: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return yearMonth.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun endOfMonthMillis(yearMonth: YearMonth, zoneId: ZoneId = ZoneId.systemDefault()): Long {
        return yearMonth.atEndOfMonth().atTime(23, 59, 59, 999_000_000)
            .atZone(zoneId).toInstant().toEpochMilli()
    }

    fun greetingForHour(hour: Int = LocalDateTime.now().hour): String {
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
