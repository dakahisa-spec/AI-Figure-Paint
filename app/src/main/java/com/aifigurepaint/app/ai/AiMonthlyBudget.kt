package com.aifigurepaint.app.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.roundToLong

data class AiBudgetEstimate(
    val inputTokens: Long,
    val outputTokens: Long,
    val webSearchCalls: Int,
    val costMilliWon: Long,
)

data class AiBudgetSnapshot(
    val month: String,
    val totalMilliWon: Long,
    val inputTokens: Long,
    val outputTokens: Long,
) {
    val totalWon: Long get() = (totalMilliWon / 1_000.0).roundToLong()
    val warningMessage: String?
        get() = when {
            totalMilliWon >= AiMonthlyBudgetStore.HARD_LIMIT_MILLI_WON ->
                "이번 달 AI 사용 한도 5,000원에 도달했습니다. 다음 달 1일부터 다시 사용할 수 있습니다."
            totalMilliWon > AiMonthlyBudgetStore.WARNING_MILLI_WON ->
                "이번 달 AI 사용금액이 약 ${"%,d".format(totalWon)}원입니다. 5,000원을 넘는 요청은 자동으로 차단됩니다."
            else -> null
        }
}

class AiBudgetLimitException : IllegalStateException(
    "이번 달 AI 사용 한도 5,000원에 도달했습니다. 다음 달 1일부터 다시 사용할 수 있습니다.",
)

class AiMonthlyBudgetStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun snapshot(): AiBudgetSnapshot {
        resetForNewMonthIfNeeded()
        return AiBudgetSnapshot(
            month = currentMonth(),
            totalMilliWon = prefs.getLong(KEY_COST_MILLI_WON, 0L),
            inputTokens = prefs.getLong(KEY_INPUT_TOKENS, 0L),
            outputTokens = prefs.getLong(KEY_OUTPUT_TOKENS, 0L),
        )
    }

    @Synchronized
    fun checkBeforeRequest(modelId: String, body: JSONObject): AiBudgetEstimate {
        val current = snapshot()
        val estimate = estimate(modelId, body)
        if (current.totalMilliWon >= HARD_LIMIT_MILLI_WON ||
            current.totalMilliWon + estimate.costMilliWon > HARD_LIMIT_MILLI_WON
        ) {
            throw AiBudgetLimitException()
        }
        return estimate
    }

    @Synchronized
    fun recordUsage(
        modelId: String,
        response: JSONObject,
        fallback: AiBudgetEstimate,
    ): AiBudgetSnapshot {
        resetForNewMonthIfNeeded()
        val usage = response.optJSONObject("usage")
        val actualInput = usage?.optLong("input_tokens", -1L)?.takeIf { it >= 0L }
        val actualOutput = usage?.optLong("output_tokens", -1L)?.takeIf { it >= 0L }
        val inputTokens = actualInput ?: fallback.inputTokens
        val outputTokens = actualOutput ?: fallback.outputTokens
        val actualSearchCalls = countWebSearchCalls(response).takeIf { it > 0 } ?: fallback.webSearchCalls
        val cost = calculateCostMilliWon(modelId, inputTokens, outputTokens, actualSearchCalls)
        prefs.edit()
            .putLong(KEY_COST_MILLI_WON, prefs.getLong(KEY_COST_MILLI_WON, 0L) + cost)
            .putLong(KEY_INPUT_TOKENS, prefs.getLong(KEY_INPUT_TOKENS, 0L) + inputTokens)
            .putLong(KEY_OUTPUT_TOKENS, prefs.getLong(KEY_OUTPUT_TOKENS, 0L) + outputTokens)
            .apply()
        return snapshot()
    }

    private fun estimate(modelId: String, body: JSONObject): AiBudgetEstimate {
        val inputTokens = estimateInputTokens(body).coerceAtLeast(1L)
        val outputTokens = body.optLong("max_output_tokens", DEFAULT_OUTPUT_TOKENS)
            .coerceIn(1L, MAX_ESTIMATED_OUTPUT_TOKENS)
        val webSearchCalls = body.optJSONArray("tools")?.let { tools ->
            (0 until tools.length()).count { index ->
                tools.optJSONObject(index)?.optString("type").orEmpty().contains("web_search")
            }
        } ?: 0
        return AiBudgetEstimate(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            webSearchCalls = webSearchCalls,
            costMilliWon = calculateCostMilliWon(modelId, inputTokens, outputTokens, webSearchCalls),
        )
    }

    private fun estimateInputTokens(value: Any?): Long = when (value) {
        is JSONObject -> value.keys().asSequence().sumOf { estimateInputTokens(value.opt(it)) }
        is JSONArray -> (0 until value.length()).sumOf { estimateInputTokens(value.opt(it)) }
        is String -> if (value.startsWith("data:image/", ignoreCase = true)) {
            IMAGE_INPUT_TOKEN_ESTIMATE
        } else {
            ((value.length + 3L) / 4L).coerceAtLeast(1L)
        }
        else -> 0L
    }

    private fun countWebSearchCalls(response: JSONObject): Int {
        val output = response.optJSONArray("output") ?: return 0
        return (0 until output.length()).count { index ->
            output.optJSONObject(index)?.optString("type").orEmpty().contains("web_search")
        }
    }

    private fun calculateCostMilliWon(
        modelId: String,
        inputTokens: Long,
        outputTokens: Long,
        webSearchCalls: Int,
    ): Long {
        val prices = if (modelId == AiModelRouter.LUNA_MODEL) LUNA_PRICES else TERRA_PRICES
        val tokenCostUsd = inputTokens.toDouble() / 1_000_000.0 * prices.first +
            outputTokens.toDouble() / 1_000_000.0 * prices.second
        val searchCostUsd = webSearchCalls * WEB_SEARCH_USD
        return ((tokenCostUsd + searchCostUsd) * KRW_PER_USD * 1_000.0).roundToLong().coerceAtLeast(1L)
    }

    private fun resetForNewMonthIfNeeded() {
        val month = currentMonth()
        if (prefs.getString(KEY_MONTH, null) == month) return
        prefs.edit()
            .putString(KEY_MONTH, month)
            .putLong(KEY_COST_MILLI_WON, 0L)
            .putLong(KEY_INPUT_TOKENS, 0L)
            .putLong(KEY_OUTPUT_TOKENS, 0L)
            .apply()
    }

    private fun currentMonth(): String = YearMonth.now(KOREA_ZONE).toString()

    companion object {
        const val WARNING_MILLI_WON = 3_000_000L
        const val HARD_LIMIT_MILLI_WON = 5_000_000L

        private const val PREFS_NAME = "ai_monthly_budget"
        private const val KEY_MONTH = "month"
        private const val KEY_COST_MILLI_WON = "cost_milli_won"
        private const val KEY_INPUT_TOKENS = "input_tokens"
        private const val KEY_OUTPUT_TOKENS = "output_tokens"
        private const val DEFAULT_OUTPUT_TOKENS = 2_000L
        private const val MAX_ESTIMATED_OUTPUT_TOKENS = 8_000L
        private const val IMAGE_INPUT_TOKEN_ESTIMATE = 5_000L
        private const val KRW_PER_USD = 1_400.0
        private const val WEB_SEARCH_USD = 0.01
        private val LUNA_PRICES = 0.20 to 1.20
        private val TERRA_PRICES = 2.00 to 12.00
        private val KOREA_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
