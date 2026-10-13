package com.pooriapm.momentum.onboarding

import java.time.LocalDate
import java.time.Period

/**
 * Onboarding answers use the exact keys and values of the website (Momentum/src/v2/onboarding),
 * because the server's complete_onboarding reads the same `onboarding_drafts` payload.
 * The app asks one question (or one tight group) per screen; [section] is the website step the
 * draft is saved under, so a user can finish on either client.
 */
enum class Step(val section: String) {
    Intro("basics"),
    Name("basics"),
    Goal("goal"),
    Sex("basics"),
    Birth("basics"),
    BodySize("basics"),
    Target("goal"),
    Summary("goal"),
    Country("basics"),
    Health("health"),
    Diet("food"),
    Foods("food"),
    Meals("food"),
    Budget("food"),
    TrainingDays("training"),
    TrainingStyle("training"),
    TrainingTime("training"),
    Schedule("training"),
    Consent("consent"),
    Review("review"),
}

typealias Answers = Map<String, String>

fun Answers.int(key: String, default: Int = 0) = this[key]?.toIntOrNull() ?: default
fun Answers.dbl(key: String) = this[key]?.toDoubleOrNull()
fun Answers.list(key: String) = this[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

val defaultAnswers: Answers = mapOf(
    "requestedMealCount" to "3",
    "preferredOptionCount" to "3",
    "restaurantMealsPerWeek" to "0",
    "trainingDays" to "0",
    "trainingDurationPreset" to "60",
    "trainingDuration" to "60",
    "country" to "IR",
    // The app offers the Momentum-built plan; importing an outside plan stays on the website.
    "planSource" to "momentum",
    // Body-composition evidence is optional and stays on the website for now.
    "bodySkipped" to "yes",
)

fun isVisible(step: Step, a: Answers): Boolean = when (step) {
    Step.Target -> a["goalType"] == "fat_loss" || a["goalType"] == "muscle_gain"
    Step.TrainingStyle, Step.TrainingTime -> a.int("trainingDays") > 0
    else -> true
}

fun ageOf(birthIso: String?, today: LocalDate = LocalDate.now()): Int? =
    birthIso?.let { runCatching { Period.between(LocalDate.parse(it), today).years }.getOrNull() }

fun bmi(a: Answers): Double? {
    val h = a.dbl("heightCm") ?: return null
    val w = a.dbl("weightKg") ?: return null
    return w / ((h / 100) * (h / 100))
}

enum class HealthOutcome { Eligible, Incomplete, Blocked, Urgent }

private val healthQuestions = listOf("pregnancyOrBreastfeeding", "eatingDisorderHistory", "highRiskCondition", "urgentSymptoms", "injuryLimitation")

fun healthOutcome(a: Answers): HealthOutcome = when {
    a["urgentSymptoms"] == "yes" -> HealthOutcome.Urgent
    a["pregnancyOrBreastfeeding"] == "yes" || a["eatingDisorderHistory"] == "yes" || a["highRiskCondition"] == "yes" -> HealthOutcome.Blocked
    healthQuestions.any { a[it].isNullOrBlank() } -> HealthOutcome.Incomplete
    else -> HealthOutcome.Eligible
}

private fun inRange(value: Double?, min: Double, max: Double) = value != null && value in min..max

/** Same limits as the website schema; returns true when the step can continue. */
fun isComplete(step: Step, a: Answers): Boolean = when (step) {
    Step.Intro, Step.Summary, Step.Review -> true
    Step.Name -> !a["firstName"].isNullOrBlank()
    Step.Goal -> a["goalType"] in setOf("fat_loss", "muscle_gain", "maintenance")
    Step.Sex -> a["sex"] in setOf("female", "male", "undisclosed")
    Step.Birth -> (ageOf(a["birthDate"]) ?: 0) >= 18
    Step.BodySize -> inRange(a.dbl("heightCm"), 120.0, 230.0) && inRange(a.dbl("weightKg"), 35.0, 350.0)
    Step.Target -> a["targetWeightKg"].isNullOrBlank() || inRange(a.dbl("targetWeightKg"), 35.0, 350.0)
    Step.Country -> a["country"]?.length == 2
    Step.Health -> healthOutcome(a) == HealthOutcome.Eligible
    Step.Diet -> a["dietStyle"] in setOf("omnivore", "vegetarian") && ("other" !in a.list("allergies") || !a["otherAllergy"].isNullOrBlank())
    Step.Foods -> true
    Step.Meals -> a.int("requestedMealCount") in 2..6 && a.int("preferredOptionCount") in 1..4
    Step.Budget -> a.int("restaurantMealsPerWeek") == 0 || !a["restaurantPreferences"].isNullOrBlank()
    Step.TrainingDays -> a.int("trainingDays") == 0 || a.list("trainingWeekdays").size == a.int("trainingDays")
    Step.TrainingStyle -> !a["trainingLocation"].isNullOrBlank() && !a["primaryActivity"].isNullOrBlank() && !a["trainingExperience"].isNullOrBlank()
    Step.TrainingTime -> !a["trainingStartTime"].isNullOrBlank() && !a["trainingAvailability"].isNullOrBlank() &&
        (a["trainingDurationPreset"] != "custom" || a.int("trainingDuration") in 15..120)
    Step.Schedule -> !a["workSchedule"].isNullOrBlank()
    Step.Consent -> listOf("termsAccepted", "privacyAccepted", "healthDataConsent").all { a[it] == "yes" }
}

private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"

/** Mirrors prepareCompletionValues on the website before the final save. */
fun prepareForCompletion(input: Answers): Answers {
    val next = input.toMutableMap()
    if (next["goalType"] == "maintenance") next.remove("targetWeightKg")
    else if (next["targetWeightKg"].isNullOrBlank()) next["weightKg"]?.let { next["targetWeightKg"] = it }

    if (next.int("trainingDays") > 0) {
        val preset = next["trainingDurationPreset"]
        if (preset != null && preset != "custom") next["trainingDuration"] = preset
    } else {
        listOf("trainingDurationPreset", "trainingDuration", "trainingLocation", "primaryActivity", "trainingExperience",
            "trainingWeekdays", "trainingStartTime", "trainingAvailability", "equipment").forEach(next::remove)
    }
    if (next.int("restaurantMealsPerWeek") == 0) next.remove("restaurantPreferences")
    if (next["trainingLocation"] == "outdoor") next.remove("equipment")
    if ("other" !in next.list("allergies")) next.remove("otherAllergy")

    val count = next["requestedMealCount"]?.takeIf { it in setOf("2", "3", "4", "5", "6") } ?: "3"
    val label = "${PERSIAN_DIGITS[count.toInt()]} وعده"
    val notes = next["requestedMealPattern"]?.trim().orEmpty()
    next["requestedMealPattern"] = when {
        notes.isEmpty() || notes == label -> label
        notes.startsWith("$label.") || notes.startsWith("$label ") -> notes
        else -> "$label. $notes"
    }
    next["preferredOptionCount"] = next.int("preferredOptionCount", 3).coerceIn(1, 4).toString()
    next["adultConfirmed"] = if ((ageOf(next["birthDate"]) ?: 0) >= 18) "yes" else "no"
    return next.filterValues { it.isNotBlank() }
}
