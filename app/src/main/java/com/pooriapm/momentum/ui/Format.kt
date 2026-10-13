package com.pooriapm.momentum.ui

import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.roundToInt

private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Replaces Latin digits with Persian ones. */
fun String.fa(): String = map { if (it in '0'..'9') PERSIAN_DIGITS[it - '0'] else it }.joinToString("")

fun Int.fa(): String = toString().fa()
fun Double.faRounded(): String = roundToInt().toString().fa()

private val JALALI_MONTHS = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

/** Gregorian ISO date (yyyy-mm-dd or a timestamp) → "۱۸ مهر ۱۴۰۵". */
fun jalaliLabel(iso: String): String {
    val date = runCatching { LocalDate.parse(iso.take(10)) }
        .recoverCatching { OffsetDateTime.parse(iso).toLocalDate() }
        .getOrNull() ?: return iso
    val (y, m, d) = toJalali(date.year, date.monthValue, date.dayOfMonth)
    return "${d.fa()} ${JALALI_MONTHS[m - 1]} ${y.fa()}"
}

// Standard Gregorian → Jalali conversion (same arithmetic as jalaali-js).
private fun toJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
    val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gDaysInMonth[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    return Triple(jy, jm, jd)
}
