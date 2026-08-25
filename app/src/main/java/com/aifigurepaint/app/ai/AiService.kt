package com.aifigurepaint.app.ai

import android.content.Context
import android.graphics.Color
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.aifigurepaint.app.data.PaintEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.coroutines.coroutineContext
import kotlin.math.pow
import kotlin.math.sqrt

data class AiMixComponent(
    val paintId: Long,
    val paintName: String,
    val productCode: String?,
    val percent: Double,
)

data class AiMixSuggestion(
    val name: String,
    val targetHex: String,
    val components: List<AiMixComponent>,
    val explanation: String,
    val source: String,
    val originalPrompt: String,
)

data class AiMixRequest(
    val prompt: String,
    val paints: List<PaintEntity>,
    val ownedOnly: Boolean = true,
    val brand: String? = null,
    val currentRecipe: String? = null,
    val targetHex: String? = null,
)

data class AiPaintDraft(
    val brand: String,
    val series: String,
    val productCode: String?,
    val name: String,
    val koreanName: String,
    val colorHex: String,
    val confidence: Double,
    val notes: String,
)

data class AiProjectDraft(
    val projectName: String,
    val modelName: String,
    val startDate: String,
    val status: String,
    val memo: String,
    val confidence: Double,
    val notes: String,
)

interface AiService {
    suspend fun suggestMix(request: AiMixRequest): AiMixSuggestion
    suspend fun advise(question: String, context: String): String
    suspend fun analyzePaintPhoto(imageDataUrl: String): AiPaintDraft
    suspend fun analyzeProjectPhoto(imageDataUrl: String, captureDate: String): AiProjectDraft
}

class OpenAiService(
    private val apiKey: String,
    private val model: String = AiSettingsStore.DEFAULT_MODEL,
) : AiService {
    override suspend fun suggestMix(request: AiMixRequest): AiMixSuggestion {
        val available = request.paints
            .filter { !request.ownedOnly || it.owned }
            .filter { request.brand.isNullOrBlank() || it.brand == request.brand }
        require(available.isNotEmpty()) { "조건에 맞는 도료가 없습니다." }

        val inventory = available.joinToString("\n") {
            "id=${it.id}; ${it.brand}; ${it.productCode.orEmpty()}; ${it.name}; ${it.koreanName}; hex=${hex(it.colorValue)}"
        }
        val input = buildString {
            appendLine("사용자의 목표: ${request.prompt}")
            request.targetHex?.let { appendLine("사진/로컬 분석 목표색: $it") }
            request.currentRecipe?.let { appendLine("현재 레시피(직접 덮어쓰지 말고 수정안만 제안): $it") }
            appendLine("사용 가능한 도료 목록:")
            append(inventory)
        }
        val body = JSONObject()
            .put("model", model)
            .put("store", false)
            .put(
                "instructions",
                "당신은 피규어 도색 조색 보조자입니다. 목록에 있는 도료만 사용해 2~5개 성분을 고르고 percent 합계를 100으로 맞추세요. 실제 안료 특성은 완벽히 시뮬레이션할 수 없으므로 짧고 보수적으로 설명하세요. 기존 레시피나 DB를 변경하지 말고 제안만 반환하세요.",
            )
            .put("input", input)
            .put(
                "text",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", "paint_mix_suggestion")
                        .put("strict", true)
                        .put(
                            "schema",
                            JSONObject()
                                .put("type", "object")
                                .put("additionalProperties", false)
                                .put("required", JSONArray(listOf("name", "target_hex", "explanation", "components")))
                                .put(
                                    "properties",
                                    JSONObject()
                                        .put("name", JSONObject().put("type", "string"))
                                        .put("target_hex", JSONObject().put("type", "string"))
                                        .put("explanation", JSONObject().put("type", "string"))
                                        .put(
                                            "components",
                                            JSONObject()
                                                .put("type", "array")
                                                .put("minItems", 2)
                                                .put("maxItems", 5)
                                                .put(
                                                    "items",
                                                    JSONObject()
                                                        .put("type", "object")
                                                        .put("additionalProperties", false)
                                                        .put("required", JSONArray(listOf("paint_id", "percent")))
                                                        .put(
                                                            "properties",
                                                            JSONObject()
                                                                .put("paint_id", JSONObject().put("type", "integer"))
                                                                .put("percent", JSONObject().put("type", "number")),
                                                        ),
                                                ),
                                        ),
                                ),
                        ),
                ),
            )

        val result = post(body)
        val json = JSONObject(extractOutputText(result))
        val components = buildList {
            val rows = json.getJSONArray("components")
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val paint = available.firstOrNull { it.id == row.getLong("paint_id") } ?: continue
                add(AiMixComponent(paint.id, paint.name, paint.productCode, row.getDouble("percent").coerceAtLeast(0.0)))
            }
        }
        require(components.size >= 2) { "AI가 사용할 수 없는 도료를 제안했습니다." }
        val sum = components.sumOf { it.percent }.takeIf { it > 0.0 } ?: 100.0
        return AiMixSuggestion(
            name = json.optString("name", "AI 조색 제안"),
            targetHex = normalizeHex(json.optString("target_hex", request.targetHex ?: "#808080")),
            components = components.map { it.copy(percent = it.percent / sum * 100.0) },
            explanation = json.optString("explanation", "보유 도료를 기준으로 계산한 예상 조색입니다."),
            source = "OpenAI · $model",
            originalPrompt = request.prompt,
        )
    }

    override suspend fun advise(question: String, context: String): String {
        val body = JSONObject()
            .put("model", model)
            .put("store", false)
            .put(
                "instructions",
                "피규어 도색 작업을 돕는 전문 어시스턴트입니다. 프로젝트 정보에 근거해 한국어로 4문장 이내로 답하고, 불확실한 도료/희석/압력은 테스트 피스를 권하세요. DB를 직접 변경한다고 표현하지 마세요.",
            )
            .put("input", "프로젝트 정보:\n$context\n\n질문: $question")
        return extractOutputText(post(body)).trim()
    }

    override suspend fun analyzePaintPhoto(imageDataUrl: String): AiPaintDraft {
        val content = JSONArray()
            .put(
                JSONObject()
                    .put("type", "input_text")
                    .put(
                        "text",
                        """
                        이 사진은 피규어·모형 도색용 도료 용기 또는 라벨입니다.
                        사진에 실제로 보이는 정보만 근거로 브랜드, 시리즈, 제품 코드, 제품명과 한글 제품명을 판독하세요.
                        불명확한 항목은 추측하지 말고 빈 문자열로 두세요.
                        color_hex는 라벨의 색상칩이나 내용물에서 추정되는 대표 도료색을 #RRGGBB로 반환하세요.
                        notes에는 사용자가 저장 전 확인해야 할 불확실한 부분을 한국어 한두 문장으로 적으세요.
                        이 결과는 DB에 자동 저장되지 않는 검토용 초안입니다.
                        """.trimIndent(),
                    ),
            )
            .put(
                JSONObject()
                    .put("type", "input_image")
                    .put("image_url", imageDataUrl)
                    .put("detail", "high"),
            )
        val properties = JSONObject()
            .put("brand", JSONObject().put("type", "string"))
            .put("series", JSONObject().put("type", "string"))
            .put("product_code", JSONObject().put("type", "string"))
            .put("name", JSONObject().put("type", "string"))
            .put("korean_name", JSONObject().put("type", "string"))
            .put("color_hex", JSONObject().put("type", "string"))
            .put(
                "confidence",
                JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1),
            )
            .put("notes", JSONObject().put("type", "string"))
        val body = JSONObject()
            .put("model", model)
            .put("store", false)
            .put("reasoning", JSONObject().put("effort", "low"))
            .put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", content),
                ),
            )
            .put(
                "text",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", "paint_photo_draft")
                        .put("strict", true)
                        .put(
                            "schema",
                            JSONObject()
                                .put("type", "object")
                                .put("additionalProperties", false)
                                .put(
                                    "required",
                                    JSONArray(
                                        listOf(
                                            "brand",
                                            "series",
                                            "product_code",
                                            "name",
                                            "korean_name",
                                            "color_hex",
                                            "confidence",
                                            "notes",
                                        ),
                                    ),
                                )
                                .put("properties", properties),
                        ),
                ),
            )
        val json = JSONObject(extractOutputText(post(body)))
        return AiPaintDraft(
            brand = json.optString("brand").trim(),
            series = json.optString("series").trim(),
            productCode = json.optString("product_code").trim().ifBlank { null },
            name = json.optString("name").trim(),
            koreanName = json.optString("korean_name").trim(),
            colorHex = normalizeHex(json.optString("color_hex")),
            confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
            notes = json.optString("notes").trim(),
        )
    }

    override suspend fun analyzeProjectPhoto(imageDataUrl: String, captureDate: String): AiProjectDraft {
        val content = JSONArray()
            .put(
                JSONObject()
                    .put("type", "input_text")
                    .put(
                        "text",
                        """
                        이 사진은 피규어·프라모델·레진킷 도색 프로젝트를 등록하기 위한 사진입니다.
                        사진에 실제로 보이는 박스명, 키트명, 캐릭터명, 모델명과 작업 진행 상태만 근거로 검토용 초안을 만드세요.
                        project_name은 사용자가 목록에서 알아보기 쉬운 짧은 프로젝트 이름, model_name은 식별 가능한 공식 모델·키트명입니다.
                        글자나 제품을 식별할 수 없으면 추측하지 말고 해당 문자열을 비워두세요.
                        사진만으로 실제 시작일을 확정할 수 없으므로, 명확한 날짜 표기가 없다면 촬영일 $captureDate 를 start_date 초안으로 사용하세요.
                        status는 미조립·시작 전이면 PLANNED, 조립·표면정리·도색 중이면 IN_PROGRESS, 완성작이면 COMPLETED 중 하나로 반환하세요.
                        memo에는 사진에서 확인한 상태와 추천 다음 작업을 한국어 두 문장 이내로, notes에는 불확실하거나 저장 전 확인할 항목을 적으세요.
                        이 결과는 DB에 자동 저장되지 않으며 사용자가 검토하고 명시적으로 저장할 초안입니다.
                        """.trimIndent(),
                    ),
            )
            .put(
                JSONObject()
                    .put("type", "input_image")
                    .put("image_url", imageDataUrl)
                    .put("detail", "high"),
            )
        val properties = JSONObject()
            .put("project_name", JSONObject().put("type", "string"))
            .put("model_name", JSONObject().put("type", "string"))
            .put("start_date", JSONObject().put("type", "string"))
            .put(
                "status",
                JSONObject().put("type", "string").put(
                    "enum",
                    JSONArray(listOf("PLANNED", "IN_PROGRESS", "COMPLETED")),
                ),
            )
            .put("memo", JSONObject().put("type", "string"))
            .put(
                "confidence",
                JSONObject().put("type", "number").put("minimum", 0).put("maximum", 1),
            )
            .put("notes", JSONObject().put("type", "string"))
        val body = JSONObject()
            .put("model", model)
            .put("store", false)
            .put("reasoning", JSONObject().put("effort", "low"))
            .put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", content),
                ),
            )
            .put(
                "text",
                JSONObject().put(
                    "format",
                    JSONObject()
                        .put("type", "json_schema")
                        .put("name", "project_photo_draft")
                        .put("strict", true)
                        .put(
                            "schema",
                            JSONObject()
                                .put("type", "object")
                                .put("additionalProperties", false)
                                .put(
                                    "required",
                                    JSONArray(
                                        listOf(
                                            "project_name",
                                            "model_name",
                                            "start_date",
                                            "status",
                                            "memo",
                                            "confidence",
                                            "notes",
                                        ),
                                    ),
                                )
                                .put("properties", properties),
                        ),
                ),
            )
        val json = JSONObject(extractOutputText(post(body)))
        val status = json.optString("status").takeIf {
            it == "PLANNED" || it == "IN_PROGRESS" || it == "COMPLETED"
        } ?: "PLANNED"
        val startDate = Regex("\\d{4}-\\d{2}-\\d{2}")
            .find(json.optString("start_date"))?.value ?: captureDate
        return AiProjectDraft(
            projectName = json.optString("project_name").trim(),
            modelName = json.optString("model_name").trim(),
            startDate = startDate,
            status = status,
            memo = json.optString("memo").trim(),
            confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0),
            notes = json.optString("notes").trim(),
        )
    }

    private suspend fun post(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL("https://api.openai.com/v1/responses").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            coroutineContext.ensureActive()
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            coroutineContext.ensureActive()
            if (code !in 200..299) {
                val apiMessage = runCatching { JSONObject(text).getJSONObject("error").optString("message") }.getOrNull()
                error(apiMessage?.take(180) ?: "AI 연결 오류 ($code)")
            }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractOutputText(response: JSONObject): String {
        response.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        val output = response.optJSONArray("output") ?: error("AI 응답에 출력이 없습니다.")
        for (i in 0 until output.length()) {
            val content = output.getJSONObject(i).optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val item = content.getJSONObject(j)
                if (item.optString("type") == "output_text") return item.getString("text")
            }
        }
        error("AI 응답을 읽을 수 없습니다.")
    }
}

object LocalColorEngine {
    fun suggest(request: AiMixRequest): AiMixSuggestion {
        val available = request.paints
            .filter { !request.ownedOnly || it.owned }
            .filter { request.brand.isNullOrBlank() || it.brand == request.brand }
        require(available.isNotEmpty()) { "조건에 맞는 도료가 없습니다." }
        val target = parseTarget(request.targetHex ?: request.prompt)
        val closest = available.sortedBy { distance(it.colorValue, target) }.take(minOf(4, available.size))
        val raw = closest.map { 1.0 / (distance(it.colorValue, target) + 16.0) }
        val total = raw.sum()
        val components = closest.mapIndexed { index, paint ->
            AiMixComponent(paint.id, paint.name, paint.productCode, raw[index] / total * 100.0)
        }
        return AiMixSuggestion(
            name = request.prompt.trim().take(28).ifBlank { "로컬 색상 후보" },
            targetHex = hex(target),
            components = components,
            explanation = "RGB 색상 거리가 가까운 보유 도료를 기준으로 만든 로컬 후보입니다. 실제 혼색은 테스트 피스에서 소량부터 확인하세요.",
            source = "로컬 색상 거리",
            originalPrompt = request.prompt,
        )
    }

    private fun parseTarget(text: String): Int {
        Regex("#?[0-9a-fA-F]{6}").find(text)?.value?.removePrefix("#")?.toLongOrNull(16)?.let {
            return (0xFF000000L or it).toInt()
        }
        val lower = text.lowercase()
        return when {
            "보라" in lower || "purple" in lower || "violet" in lower -> Color.rgb(133, 91, 158)
            "핑크" in lower || "pink" in lower -> Color.rgb(224, 137, 158)
            "빨" in lower || "red" in lower -> Color.rgb(168, 39, 52)
            "파랑" in lower || "blue" in lower -> Color.rgb(50, 83, 139)
            "초록" in lower || "green" in lower -> Color.rgb(70, 112, 77)
            "노랑" in lower || "yellow" in lower -> Color.rgb(222, 186, 48)
            "주황" in lower || "orange" in lower -> Color.rgb(219, 121, 43)
            "검" in lower || "black" in lower -> Color.rgb(37, 39, 43)
            "흰" in lower || "white" in lower -> Color.rgb(235, 233, 226)
            else -> Color.rgb(141, 128, 132)
        }
    }

    private fun distance(a: Int, b: Int): Double = sqrt(
        (Color.red(a) - Color.red(b)).toDouble().pow(2) +
            (Color.green(a) - Color.green(b)).toDouble().pow(2) +
            (Color.blue(a) - Color.blue(b)).toDouble().pow(2),
    )
}

class AiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_provider_settings", Context.MODE_PRIVATE)

    fun hasApiKey(): Boolean = prefs.contains(KEY_VALUE)
    fun model(): String = DEFAULT_MODEL
    fun maskedKey(): String = if (hasApiKey()) "••••••••${readApiKey().takeLast(4)}" else "미설정"

    fun save(apiKey: String, model: String) {
        if (apiKey.isNotBlank()) prefs.edit().putString(KEY_VALUE, encrypt(apiKey.trim())).apply()
        prefs.edit().putString(KEY_MODEL, DEFAULT_MODEL).apply()
    }

    fun clear() = prefs.edit().remove(KEY_VALUE).apply()
    fun readApiKey(): String = prefs.getString(KEY_VALUE, null)?.let(::decrypt).orEmpty()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val combined = cipher.iv + cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String = runCatching {
        val combined = Base64.decode(value, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
    }.getOrDefault("")

    companion object {
        const val DEFAULT_MODEL = "gpt-5.6"
        private const val KEY_ALIAS = "ai_figure_paint_api_key"
        private const val KEY_VALUE = "encrypted_api_key"
        private const val KEY_MODEL = "model"
    }
}

private fun hex(value: Int): String = "#%06X".format(value and 0xFFFFFF)
private fun normalizeHex(value: String): String = Regex("#?[0-9a-fA-F]{6}").find(value)?.value?.let {
    "#${it.removePrefix("#").uppercase()}"
} ?: "#808080"
