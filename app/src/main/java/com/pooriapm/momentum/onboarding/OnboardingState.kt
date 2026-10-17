package com.pooriapm.momentum.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.ApiException
import com.pooriapm.momentum.data.ErrorKind
import com.pooriapm.momentum.data.asApi
import com.pooriapm.momentum.data.persianError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.TimeZone
import java.util.UUID

enum class BuildPhase { Idle, Completing, Generating, Ready, Waiting, Blocked, Failed }

class OnboardingState(
    private val api: Api,
    private val scope: CoroutineScope,
    private val isOnline: () -> Boolean,
    private val onFinished: () -> Unit,
) {
    var answers by mutableStateOf(defaultAnswers)
        private set
    var step by mutableStateOf(Step.Intro)
        private set
    var loaded by mutableStateOf(false)
        private set

    /** The saved draft could not be read. Starting empty would overwrite it on the next save. */
    var loadError by mutableStateOf<ApiException?>(null)
        private set
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    /** The last background save failed; the next save sends the whole draft again. */
    var unsynced by mutableStateOf(false)
        private set
    var phase by mutableStateOf(BuildPhase.Idle)
        private set
    var phaseMessage by mutableStateOf<String?>(null)
        private set

    /** After "edit" on the review screen, Continue jumps straight back to review. */
    private var returnToReview = false
    private var generationKey: String? = null

    /** AI generation was off, so the catalog starter plan was activated instead. */
    var usedStarterPlan by mutableStateOf(false)
        private set

    val visibleSteps: List<Step> get() = Step.entries.filter { isVisible(it, answers) }
    val progress: Float get() = (visibleSteps.indexOf(step) + 1).toFloat() / visibleSteps.size
    val stepLabel: String get() = "${(visibleSteps.indexOf(step) + 1)}/${visibleSteps.size}"

    fun load() {
        loadError = null
        scope.launch {
            try {
                api.loadDraft()?.let { draft ->
                    // Blank values (the website saves untouched fields as "") must not hide the defaults.
                    answers = defaultAnswers + draft.values.filterValues { it.isNotBlank() }
                    // Resume at the first unfinished screen of the saved section.
                    step = visibleSteps.firstOrNull { it.section == draft.step && !isComplete(it, answers) }
                        ?: visibleSteps.firstOrNull { !isComplete(it, answers) && it != Step.Intro }
                        ?: Step.Review
                    if (draft.values.isEmpty()) step = Step.Intro
                }
                if (answers["onboardingFlowId"].isNullOrBlank()) set("onboardingFlowId", UUID.randomUUID().toString())
                loaded = true
            } catch (raw: Exception) {
                val e = raw.asApi()
                if (api.session == null) onFinished() else loadError = e
            }
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
        error = null
        step = if (returnToReview) Step.Review.also { returnToReview = false }
        else steps.getOrElse(steps.indexOf(step) + 1) { Step.Review }
    }

    fun back() {
        val steps = visibleSteps
        steps.getOrNull(steps.indexOf(step) - 1)?.let { error = null; step = it }
    }

    fun edit(target: Step) {
        returnToReview = true
        error = null
        step = target
    }

    private fun withContext(values: Answers) = values + mapOf(
        "locale" to "fa-IR",
        "timezone" to (TimeZone.getDefault().id ?: "Asia/Tehran"),
    )

    /**
     * Saves after every screen so the user can resume on any device. The draft is a full upsert,
     * so a failed save is simply repeated by the next one; the user only sees a quiet note.
     */
    private fun save() {
        val snapshot = withContext(answers)
        val section = step.section
        scope.launch {
            try {
                api.saveDraft(section, snapshot)
                unsynced = false
            } catch (raw: Exception) {
                raw.asApi()
                if (api.session == null) onFinished() else unsynced = true
            }
        }
    }

    fun finish() {
        if (phase == BuildPhase.Completing || phase == BuildPhase.Generating || saving) return
        if (!isOnline()) {
            error = persianError("offline", ErrorKind.Offline)
            return
        }
        val payload = prepareForCompletion(withContext(answers))
        // Check the server's own rules first: the server only says "invalid", the app can say where.
        serverRuleViolations(payload).firstOrNull()?.let { (rule, fix) ->
            if (com.pooriapm.momentum.BuildConfig.DEBUG) android.util.Log.w("MomentumOnboarding", "draft rule failed: $rule")
            returnToReview = true
            step = fix
            error = "این بخش هنوز کامل یا معتبر نیست. اصلاحش کن و دوباره تأیید کن."
            return
        }
        val flowId = payload["onboardingFlowId"] ?: UUID.randomUUID().toString()
        scope.launch {
            saving = true
            error = null
            phase = BuildPhase.Completing
            try {
                api.saveDraft("review", payload)
                unsynced = false
                val status = api.completeOnboarding("$flowId:complete:${fingerprint(payload)}")
                if (status == "automation_blocked") {
                    phase = BuildPhase.Blocked
                    phaseMessage = "اطلاعات ذخیره شد، اما برنامه‌ریزی خودکار برای شرایط ثبت‌شده مناسب نیست. لطفاً با یک متخصص سلامت مشورت کن."
                    return@launch
                }
                generationKey = "$flowId:generate"
                generate()
            } catch (raw: Exception) {
                val e = raw.asApi()
                phase = BuildPhase.Idle
                if (api.session == null) onFinished() else error = e.message
            } finally {
                saving = false
            }
        }
    }

    /** Retries the plan job with the same idempotency key, so no second job is created. */
    fun retryGeneration() {
        if (generationKey == null || phase == BuildPhase.Generating) return
        if (!isOnline()) {
            phaseMessage = persianError("offline", ErrorKind.Offline)
            return
        }
        scope.launch { generate() }
    }

    private suspend fun generate() {
        val key = generationKey ?: return
        phase = BuildPhase.Generating
        phaseMessage = null
        try {
            usedStarterPlan = api.startMonthlyPlan(key)
            // The plan is imported asynchronously; wait until it shows on the dashboard.
            // A dropped connection while waiting is not a failure: keep polling.
            repeat(48) {
                val ready = try {
                    api.dashboard().plan != null
                } catch (raw: Exception) {
                    val e = raw.asApi()
                    if (!e.retryable) throw e
                    false
                }
                if (ready) {
                    phase = BuildPhase.Ready
                    return
                }
                delay(5000)
            }
            phase = BuildPhase.Waiting
        } catch (raw: Exception) {
            val e = raw.asApi()
            if (api.session == null) return onFinished()
            val needsMembership = e.code in setOf("entitlement_required", "PAYMENT_METHOD_REQUIRED", "quota_exceeded")
            phase = if (needsMembership) BuildPhase.Blocked else BuildPhase.Failed
            phaseMessage = if (needsMembership) "راه‌اندازی کامل شد. برای ساخت برنامه این ماه، عضویت را از «حساب من» فعال کن." else e.message
        }
    }

    fun done() = onFinished()

    private fun fingerprint(values: Answers): String {
        val canonical = values.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
    }
}
