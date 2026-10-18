package com.pooriapm.momentum.ui

import com.pooriapm.momentum.data.Plan
import com.pooriapm.momentum.data.ShoppingPurchase
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max

/**
 * Shopping as an inventory, not a checklist. For every ingredient the plan gives a daily need; the
 * server keeps what was bought. From those two:
 *
 *   used(d)  = need from the plan start through day d (past days count as eaten, per plan)
 *   stock    = bought − used(yesterday), never below zero (food bought elsewhere is not "owed")
 *   toBuy(w) = need(today … end of window w) − stock, never below zero
 *
 * So a weekly purchase lowers the plan-wide amount, a plan-wide purchase covers the week, and when
 * a new week starts the amounts reappear by themselves. Nothing has to be "unticked".
 */
enum class ShoppingScope(val wire: String) { Week("week"), Plan("plan") }

/** One ingredient within a window. */
data class ShoppingLine(
    val key: String,          // item_key|unit
    val itemKey: String,
    val name: String,
    val unit: String,
    val needInWindow: Double, // what the window's meals use
    val toBuy: Double,        // still to buy for the window, already rounded to a sensible step
    val coveredUntil: String?, // last plan day the stock covers, when it covers at least today
) {
    val covered get() = toBuy <= 0.0
}

data class ShoppingWindow(val start: String, val end: String, val days: Int)

private const val WEEK_DAYS = 7L

/**
 * The 7-day shopping week. It starts on the day of the first weekly purchase and lasts 7 days, so
 * ticks hold for that week; once it is over (or before any weekly purchase) it starts today.
 */
fun weekWindow(plan: Plan, today: String, purchases: List<ShoppingPurchase>): ShoppingWindow {
    val t = LocalDate.parse(today)
    val anchor = purchases
        .filter { it.scope == ShoppingScope.Week.wire }
        .mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }
        .filter { !it.isAfter(t) && it.plusDays(WEEK_DAYS - 1).let { end -> !end.isBefore(t) } }
        .minOrNull()
    val start = anchor ?: t
    val planEnd = LocalDate.parse(plan.validTo)
    val end = minOf(start.plusDays(WEEK_DAYS - 1), planEnd)
    return ShoppingWindow(start.toString(), end.toString(), (end.toEpochDay() - t.toEpochDay() + 1).toInt().coerceAtLeast(0))
}

fun planWindow(plan: Plan, today: String): ShoppingWindow {
    val t = LocalDate.parse(today)
    val end = LocalDate.parse(plan.validTo)
    return ShoppingWindow(today, plan.validTo, (end.toEpochDay() - t.toEpochDay() + 1).toInt().coerceAtLeast(0))
}

private data class ItemInfo(val itemKey: String, val name: String, val unit: String)

private fun itemKeyOf(name: String, ingredientId: String?) =
    ingredientId?.takeIf { it.isNotBlank() }?.let { "ingredient:$it" } ?: "name:${name.trim()}"

/** Daily need per item (key = item_key|unit) across the whole plan, from each meal's chosen option. */
private fun dailyNeeds(plan: Plan): Pair<Map<String, ItemInfo>, Map<String, Map<String, Double>>> {
    val info = LinkedHashMap<String, ItemInfo>()
    val needs = HashMap<String, MutableMap<String, Double>>() // key -> date -> amount
    plan.days.forEach { day ->
        day.meals.forEach { meal ->
            meal.chosenOption?.ingredients?.forEach { ingredient ->
                val unit = ingredient.unit.lowercase()
                val itemKey = itemKeyOf(ingredient.name, ingredient.ingredientId)
                val key = "$itemKey|$unit"
                info.putIfAbsent(key, ItemInfo(itemKey, ingredient.name.trim(), unit))
                val perDay = needs.getOrPut(key) { HashMap() }
                perDay[day.localDate] = (perDay[day.localDate] ?: 0.0) + ingredient.amount
            }
        }
    }
    return info to needs
}

/** Whole units for countable things (you cannot buy 2.3 bananas), whole grams/ml, else one decimal. */
fun roundUp(amount: Double, unit: String): Double = when {
    amount <= 0.0005 -> 0.0
    unit in setOf("piece", "slice", "serving", "cup") -> ceil(amount - 1e-9)
    unit in setOf("g", "ml") -> ceil(amount - 1e-9)
    else -> ceil(amount * 10 - 1e-9) / 10
}

fun shoppingLines(plan: Plan, today: String, window: ShoppingWindow, purchases: List<ShoppingPurchase>): List<ShoppingLine> {
    val (info, needs) = dailyNeeds(plan)
    val bought = purchases.groupBy { "${it.itemKey}|${it.unit.lowercase()}" }.mapValues { (_, p) -> p.sumOf { it.amount } }
    val dates = plan.days.map { it.localDate }.sorted()
    return info.mapNotNull { (key, item) ->
        val perDay = needs[key].orEmpty()
        val usedBeforeToday = perDay.filterKeys { it < today }.values.sum()
        val stock = max(0.0, (bought[key] ?: 0.0) - usedBeforeToday)
        val needInWindow = perDay.filterKeys { it >= today && it <= window.end }.values.sum()
        if (needInWindow <= 0.0) return@mapNotNull null
        val toBuy = roundUp(needInWindow - stock, item.unit)
        // How far the stock reaches, day by day from today.
        var left = stock
        var coveredUntil: String? = null
        for (d in dates.filter { it >= today }) {
            val use = perDay[d] ?: 0.0
            if (use > left + 1e-9) break
            left -= use
            coveredUntil = d
        }
        ShoppingLine(key, item.itemKey, item.name, item.unit, needInWindow, toBuy, coveredUntil)
    }.sortedWith(compareBy({ it.covered }, { it.name }))
}

/** The purchase to remove when the user unticks a line: the newest one for that item. */
fun lastPurchaseFor(line: ShoppingLine, purchases: List<ShoppingPurchase>): ShoppingPurchase? =
    purchases.filter { "${it.itemKey}|${it.unit.lowercase()}" == line.key }.maxByOrNull { it.localDate }
