package com.pooriapm.momentum.ui

import java.time.LocalDate
import java.time.OffsetDateTime
import kotlin.math.roundToInt

private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Replaces Latin digits with Persian ones. */
fun String.fa(): String = map { if (it in '0'..'9') PERSIAN_DIGITS[it - '0'] else it }.joinToString("")

fun Int.fa(): String = toString().fa()
fun Double.faRounded(): String = roundToInt().toString().fa()

/** At most one decimal, Persian digits and the Persian decimal separator: 75.0 → ۷۵, 75.46 → ۷۵٫۵. */
fun Double.faDecimal(): String {
    val tenths = kotlin.math.round(this * 10) / 10
    val text = if (tenths % 1.0 == 0.0) tenths.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, tenths)
    return text.replace('.', '٫').fa()
}

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

val jalaliMonths: List<String> get() = JALALI_MONTHS

fun jalaliMonthLength(jy: Int, jm: Int): Int = when {
    jm <= 6 -> 31
    jm <= 11 -> 30
    else -> if (jalaliToIso(jy, 12, 30).let { toJalali(LocalDate.parse(it)) == Triple(jy, 12, 30) }) 30 else 29
}

private fun toJalali(date: LocalDate) = toJalali(date.year, date.monthValue, date.dayOfMonth)

fun todayJalali(): Triple<Int, Int, Int> = toJalali(LocalDate.now())

/** Jalali y/m/d → Gregorian ISO date, which is what the server stores. */
fun jalaliToIso(jy: Int, jm: Int, jd: Int): String {
    val jy2 = jy + 1595
    var days = -355668 + 365 * jy2 + (jy2 / 33) * 8 + ((jy2 % 33) + 3) / 4 + jd +
        if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
    var gy = 400 * (days / 146097)
    days %= 146097
    if (days > 36524) {
        days--
        gy += 100 * (days / 36524)
        days %= 36524
        if (days >= 365) days++
    }
    gy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        gy += (days - 1) / 365
        days = (days - 1) % 365
    }
    var gd = days + 1
    val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
    val monthDays = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    var gm = 1
    while (gm <= 12 && gd > monthDays[gm]) {
        gd -= monthDays[gm]
        gm++
    }
    return "%04d-%02d-%02d".format(java.util.Locale.ROOT, gy, gm, gd)
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

/** Gregorian ISO date → Jalali (year, month, day), or null when it does not parse. */
fun isoToJalali(iso: String?): Triple<Int, Int, Int>? =
    iso?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }?.let { toJalali(it) }

/** Catalog units arrive in English (`g`, `tsp`…); shown in Persian, unknown ones as they are. */
fun unitLabel(unit: String): String = when (unit.lowercase()) {
    "g" -> "گرم"
    "ml" -> "میلی‌لیتر"
    "piece" -> "عدد"
    "tbsp" -> "قاشق غذاخوری"
    "tsp" -> "قاشق چای‌خوری"
    "cup" -> "پیمانه"
    "slice" -> "برش"
    "serving" -> "وعده"
    else -> unit
}
