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
)

@Serializable
data class Plan(
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
data class Ingredient(val name: String, val amount: Double, val unit: String)

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
