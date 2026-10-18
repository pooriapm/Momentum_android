package com.pooriapm.momentum.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Subset of the account-data `dashboard` response (Momentum/src/v2/data/contracts.ts).
// Unknown fields are ignored, so the server can grow without breaking the app.

@Serializable
data class Dashboard(
    @SerialName("local_date") val localDate: String,
    val profile: Profile,
    @SerialName("entitlement_usage") val entitlementUsage: EntitlementUsage? = null,
    val checkin: Checkin? = null,
    val plan: Plan? = null,
    @SerialName("recent_checkins") val recentCheckins: List<Checkin> = emptyList(),
    @SerialName("progress_series") val progressSeries: List<ProgressPoint> = emptyList(),
    @SerialName("plan_history") val planHistory: List<PlanHistoryItem> = emptyList(),
)

/** One monthly plan, current or past, as the dashboard lists them. */
@Serializable
data class PlanHistoryItem(
    val id: String,
    val cycle: Int = 1,
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_to") val validTo: String,
    val active: Boolean = false,
    val source: String = "",
)

@Serializable
data class Profile(
    @SerialName("display_name") val displayName: String,
    @SerialName("onboarding_status") val onboardingStatus: String,
    @SerialName("email_confirmed") val emailConfirmed: Boolean? = null,
)

@Serializable
data class EntitlementUsage(val entitlement: Entitlement)

@Serializable
data class Entitlement(
    val source: String,
    val status: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("period_start") val periodStart: String? = null,
)

@Serializable
data class Plan(
    val id: String? = null,
    @SerialName("version_id") val versionId: String? = null,
    val name: String,
    @SerialName("valid_from") val validFrom: String,
    @SerialName("valid_to") val validTo: String,
    val summary: String? = null,
    val day: PlanDay,
    val days: List<PlanDay> = emptyList(),
)

@Serializable
data class PlanDay(
    @SerialName("local_date") val localDate: String,
    @SerialName("day_index") val dayIndex: Int,
    val title: String? = null,
    val targets: Targets,
    val workout: Workout? = null,
    val meals: List<Meal> = emptyList(),
)

@Serializable
data class Targets(
    val calories: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double,
    @SerialName("fat_g") val fatG: Double,
)

@Serializable
data class Workout(
    val title: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val intensity: String,
    val warmup: List<String> = emptyList(),
    val exercises: List<Exercise> = emptyList(),
    val cooldown: List<String> = emptyList(),
    @SerialName("safety_note") val safetyNote: String? = null,
)

@Serializable
data class Exercise(
    @SerialName("exercise_key") val exerciseKey: String? = null,
    val name: String,
    val sets: Int,
    val reps: String,
    @SerialName("rest_seconds") val restSeconds: Int,
    val equipment: List<String> = emptyList(),
    @SerialName("intensity_note") val intensityNote: String? = null,
    val substitution: String? = null,
)

@Serializable
data class Meal(
    @SerialName("slot_key") val slotKey: String,
    val title: String,
    @SerialName("scheduled_time") val scheduledTime: String? = null,
    @SerialName("default_option_key") val defaultOptionKey: String,
    @SerialName("selected_option_key") val selectedOptionKey: String? = null,
    @SerialName("completion_status") val completionStatus: String,
    val options: List<MealOption> = emptyList(),
) {
    val chosenOption: MealOption?
        get() = options.firstOrNull { it.optionKey == (selectedOptionKey ?: defaultOptionKey) } ?: options.firstOrNull()
}

@Serializable
data class MealOption(
    @SerialName("option_key") val optionKey: String,
    val title: String,
    val ingredients: List<Ingredient> = emptyList(),
    val nutrition: Nutrition,
    val recipe: Recipe? = null,
    val warnings: List<String> = emptyList(),
)

@Serializable
data class Recipe(
    @SerialName("prep_minutes") val prepMinutes: Int,
    @SerialName("cook_minutes") val cookMinutes: Int,
    val steps: List<String> = emptyList(),
)

@Serializable
data class Ingredient(
    val name: String,
    val amount: Double,
    val unit: String,
    @SerialName("ingredient_id") val ingredientId: String? = null,
)

@Serializable
data class Nutrition(
    val calories: Double,
    @SerialName("protein_g") val proteinG: Double,
    @SerialName("carbs_g") val carbsG: Double = 0.0,
    @SerialName("fat_g") val fatG: Double = 0.0,
)

@Serializable
data class Checkin(
    @SerialName("local_date") val localDate: String,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("energy_score") val energyScore: Double? = null,
    @SerialName("sleep_minutes") val sleepMinutes: Int? = null,
    @SerialName("adherence_percent") val adherencePercent: Double? = null,
)

@Serializable
data class ProgressPoint(
    val week: Int,
    val adherence: Int,
    @SerialName("meals_completed") val mealsCompleted: Int,
    @SerialName("meals_planned") val mealsPlanned: Int,
    @SerialName("workouts_completed") val workoutsCompleted: Int,
    @SerialName("workouts_planned") val workoutsPlanned: Int,
    val partial: Boolean,
)

/** Body of checkins → save-daily (camelCase, like the website's dailyCheckInInputSchema). */
@Serializable
data class DailyCheckin(
    val energyScore: Int,
    val hungerScore: Int,
    val moodScore: Int,
    val recoveryScore: Int,
    val sleepMinutes: Int,
    val painScore: Int = 0,
    val painLocation: String? = null,
    val weightKg: Double? = null,
    val notes: String? = null,
    val redFlags: List<String> = emptyList(),
)

/** A shopping purchase synced with the server: [amount] of [itemKey] bought on [localDate]. */
@Serializable
data class ShoppingPurchase(
    val id: String,
    @SerialName("item_key") val itemKey: String,
    val unit: String,
    val amount: Double,
    val scope: String,
    @SerialName("local_date") val localDate: String,
)

/** A workout being done today (workout_sessions + logs), as the RPCs return it. */
@Serializable
data class WorkoutSession(
    val id: String,
    @SerialName("local_date") val localDate: String,
    @SerialName("workout_key") val workoutKey: String,
    @SerialName("workout_title") val workoutTitle: String,
    val status: String, // in_progress, paused, completed, stopped
    @SerialName("pain_reported") val painReported: Boolean = false,
    @SerialName("pain_severity") val painSeverity: Int? = null,
    @SerialName("stop_reason") val stopReason: String? = null,
    val exercises: List<WorkoutExerciseLog> = emptyList(),
) {
    val open get() = status == "in_progress" || status == "paused"
    val canFinish get() = exercises.isNotEmpty() && exercises.all { it.status == "completed" || it.status == "skipped" }
}

@Serializable
data class WorkoutExerciseLog(
    val id: String,
    @SerialName("exercise_key") val exerciseKey: String,
    val position: Int,
    @SerialName("planned_name") val plannedName: String,
    @SerialName("planned_sets") val plannedSets: Int,
    @SerialName("planned_reps") val plannedReps: String,
    @SerialName("planned_rest_seconds") val plannedRestSeconds: Int,
    val status: String, // planned, in_progress, completed, skipped
    @SerialName("substitute_name") val substituteName: String? = null,
    @SerialName("planned_substitute_exercise_id") val plannedSubstituteExerciseId: String? = null,
    @SerialName("substitute_exercise_id") val substituteExerciseId: String? = null,
    @SerialName("skip_reason") val skipReason: String? = null,
    val sets: List<WorkoutSetLog> = emptyList(),
) {
    val doneSets get() = sets.count { it.status == "completed" }
}

@Serializable
data class WorkoutSetLog(
    val id: String,
    @SerialName("set_number") val setNumber: Int,
    val status: String, // planned, completed, skipped
)

fun Dashboard.findMeal(localDate: String, slotKey: String): Meal? {
    val plan = plan ?: return null
    val day = if (plan.day.localDate == localDate) plan.day else plan.days.firstOrNull { it.localDate == localDate }
    return day?.meals?.firstOrNull { it.slotKey == slotKey }
}

/** A copy with one meal changed, in both the current day and the 30-day list. */
fun Dashboard.withMeal(localDate: String, slotKey: String, change: (Meal) -> Meal): Dashboard {
    val plan = plan ?: return this
    fun PlanDay.patched() = if (this.localDate != localDate) this
        else copy(meals = meals.map { if (it.slotKey == slotKey) change(it) else it })
    return copy(plan = plan.copy(day = plan.day.patched(), days = plan.days.map { it.patched() }))
}
