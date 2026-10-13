package com.pooriapm.momentum.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.TimeZone
import java.util.UUID

enum class BuildPhase { Idle, Completing, Generating, Ready, Waiting, Blocked, Failed }

class OnboardingState(private val api: Api, private val scope: CoroutineScope, private val onFinished: () -> Unit) {
    var answers by mutableStateOf(defaultAnswers)
        private set
    var step by mutableStateOf(Step.Intro)
        private set
    var loaded by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var phase by mutableStateOf(BuildPhase.Idle)
        private set
    var phaseMessage by mutableStateOf<String?>(null)
        private set

    /** After "edit" on the review screen, Continue jumps straight back to review. */
    private var returnToReview = false

    val visibleSteps: List<Step> get() = Step.entries.filter { isVisible(it, answers) }
    val progress: Float get() = (visibleSteps.indexOf(step) + 1).toFloat() / visibleSteps.size
    val stepLabel: String get() = "${(visibleSteps.indexOf(step) + 1)}/${visibleSteps.size}"

    fun load() {
        scope.launch {
            runCatching { api.loadDraft() }.getOrNull()?.let { draft ->
                answers = defaultAnswers + draft.values
                // Resume at the first unfinished screen of the saved section.
                step = visibleSteps.firstOrNull { it.section == draft.step && !isComplete(it, answers) }
                    ?: visibleSteps.firstOrNull { !isComplete(it, answers) && it != Step.Intro }
                    ?: Step.Review
                if (draft.step == "basics" && draft.values.isEmpty()) step = Step.Intro
            }
            if (answers["onboardingFlowId"].isNullOrBlank()) set("onboardingFlowId", UUID.randomUUID().toString())
            loaded = true
        }
    }

    fun set(key: String, value: String) {
        answers = answers + (key to value)
        error = null
    }

    fun toggle(key: String, value: String, limit: Int? = null) {
        val current = answers.list(key)
        val next = if (value in current) current - value else if (limit != null && current.size >= limit) current else current + value
        set(key, next.joinToString(","))
    }

    fun canContinue(): Boolean = isComplete(step, answers)

    fun next() {
        if (!canContinue()) return
        save()
        val steps = visibleSteps
        step = if (returnToReview) Step.Review.also { returnToReview = false }
        else steps.getOrElse(steps.indexOf(step) + 1) { Step.Review }
    }

    fun back() {
        val steps = visibleSteps
        steps.getOrNull(steps.indexOf(step) - 1)?.let { step = it }
    }

    fun edit(target: Step) {
        returnToReview = true
        step = target
    }

    private fun withContext(values: Answers) = values + mapOf(
        "locale" to "fa-IR",
        "timezone" to (TimeZone.getDefault().id ?: "Asia/Tehran"),
    )

    /** Saves quietly after every screen so the user can resume on any device. */
    private fun save() {
        val snapshot = withContext(answers)
        val section = step.section
        scope.launch { runCatching { api.saveDraft(section, snapshot) } }
    }

    fun finish() {
        if (phase == BuildPhase.Completing || phase == BuildPhase.Generating) return
        val payload = prepareForCompletion(withContext(answers))
        val flowId = payload["onboardingFlowId"] ?: UUID.randomUUID().toString()
        scope.launch {
            saving = true
            error = null
            phase = BuildPhase.Completing
            try {
                api.saveDraft("review", payload)
                val status = api.completeOnboarding("$flowId:complete:${fingerprint(payload)}")
                if (status == "automation_blocked") {
                    phase = BuildPhase.Blocked
                    phaseMessage = "اطلاعات ذخیره شد، اما برنامه‌ریزی خودکار برای شرایط ثبت‌شده مناسب نیست. لطفاً با یک متخصص سلامت مشورت کن."
                    return@launch
                }
                generate("$flowId:generate")
            } catch (e: ApiException) {
                phase = BuildPhase.Idle
                error = e.message
            } finally {
                saving = false
            }
        }
    }

    private suspend fun generate(key: String) {
        phase = BuildPhase.Generating
        try {
            var status = api.requestPlanGeneration(key)
            var attempts = 0
            while (status == "in_progress" && attempts++ < 20) {
                delay(1500)
                status = api.requestPlanGeneration(key)
            }
            // The plan is imported asynchronously; wait until it shows on the dashboard.
            repeat(48) {
                if (api.dashboard().plan != null) {
                    phase = BuildPhase.Ready
                    return
                }
                delay(5000)
            }
            phase = BuildPhase.Waiting
        } catch (e: ApiException) {
            phase = if (e.code in setOf("entitlement_required", "PAYMENT_METHOD_REQUIRED", "quota_exceeded")) BuildPhase.Blocked else BuildPhase.Failed
            phaseMessage = when (e.code) {
                "entitlement_required", "PAYMENT_METHOD_REQUIRED", "quota_exceeded" ->
                    "راه‌اندازی کامل شد. برای ساخت برنامه این ماه، عضویت را از «حساب من» فعال کن."
                else -> e.message
            }
        }
    }

    fun done() = onFinished()

    private fun fingerprint(values: Answers): String {
        val canonical = values.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}
