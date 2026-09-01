package com.aifigurepaint.app.ai

import android.content.Context
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.aifigurepaint.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

data class GiftAccessState(
    val loading: Boolean = false,
    val activated: Boolean = false,
    val deviceCount: Int = 0,
    val maxDevices: Int = 3,
    val usedKrw: Int = 0,
    val remainingKrw: Int = 3_000,
    val month: String = "",
    val nextReset: String = "",
    val notice: String? = null,
)

class GiftWorkerClient(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val baseUrl = BuildConfig.GIFT_WORKER_URL.trimEnd('/')

    fun isActivated(): Boolean = prefs.getString(KEY_LICENSE_ID, null)?.isNotBlank() == true

    suspend fun activate(code: String): GiftAccessState = withContext(Dispatchers.IO) {
        require(code.isNotBlank()) { "활성화 코드를 입력해주세요." }
        require(configured()) { "Cloudflare Worker 주소가 설정되지 않았습니다." }
        val body = JSONObject()
            .put("activation_code", code.trim())
            .put("device_hash", deviceHash())
            .put("public_key", publicKey())
            .put("app_version", BuildConfig.VERSION_NAME)
        val response = request("/v1/activate", body, 25_000)
        val licenseId = response.optString("license_id")
        require(licenseId.isNotBlank()) { "활성화 응답을 확인할 수 없습니다." }
        prefs.edit().putString(KEY_LICENSE_ID, licenseId).apply()
        response.toState(activated = true)
    }

    suspend fun usage(): GiftAccessState {
        if (!isActivated()) return GiftAccessState(notice = "선물용 활성화가 필요합니다.")
        return signedRequest("/v1/usage", JSONObject(), 20_000).toState(activated = true)
    }

    suspend fun postResponses(body: JSONObject, readTimeoutMs: Int): JSONObject {
        require(isActivated()) { "선물용 앱을 먼저 활성화해주세요." }
        return signedRequest("/v1/ai/responses", body, readTimeoutMs)
    }

    private suspend fun signedRequest(path: String, body: JSONObject, timeoutMs: Int): JSONObject = withContext(Dispatchers.IO) {
        require(configured()) { "Cloudflare Worker 주소가 설정되지 않았습니다." }
        val licenseId = prefs.getString(KEY_LICENSE_ID, null).orEmpty()
        require(licenseId.isNotBlank()) { "선물용 앱을 먼저 활성화해주세요." }
        val deviceHash = deviceHash()
        val nonce = request(
            "/v1/nonce",
            JSONObject().put("license_id", licenseId).put("device_hash", deviceHash),
            20_000,
        ).getString("nonce")
        val requestId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis().toString()
        val bodyText = body.toString()
        val payload = listOf(licenseId, deviceHash, requestId, timestamp, nonce, sha256(bodyText)).joinToString("\n")
        val signature = sign(payload)
        request(
            path,
            body,
            timeoutMs,
            mapOf(
                "X-License-Id" to licenseId,
                "X-Device-Hash" to deviceHash,
                "X-Request-Id" to requestId,
                "X-Timestamp" to timestamp,
                "X-Nonce" to nonce,
                "X-Signature" to signature,
            ),
        )
    }

    private fun request(
        path: String,
        body: JSONObject,
        timeoutMs: Int,
        headers: Map<String, String> = emptyMap(),
    ): JSONObject {
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val message = when (json.optString("code")) {
                    "DEVICE_LIMIT" -> "사용 가능한 기기 3대를 모두 등록했습니다."
                    "BUDGET_LIMIT" -> "이번 달 AI 사용 한도에 도달했습니다."
                    "INVALID_ACTIVATION" -> "활성화 코드를 확인해주세요."
                    "WORKER_QUOTA" -> "AI 중계 서비스의 무료 사용량이 초과되었습니다."
                    "DEVICE_REVOKED" -> "이 기기의 등록이 해제되었습니다."
                    else -> json.optString("message").ifBlank { "AI 서비스 연결 오류 ($code)" }
                }
                error(message.take(180))
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun configured(): Boolean = baseUrl.startsWith("https://") && !baseUrl.contains("configure-worker.invalid")

    private fun deviceHash(): String {
        val androidId = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        return sha256("${appContext.packageName}|$androidId")
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKey() {
        if (keyStore().containsAlias(KEY_ALIAS)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
        }.generateKeyPair()
    }

    private fun publicKey(): String {
        ensureKey()
        return Base64.encodeToString(keyStore().getCertificate(KEY_ALIAS).publicKey.encoded, Base64.NO_WRAP)
    }

    private fun sign(value: String): String {
        ensureKey()
        val privateKey = keyStore().getKey(KEY_ALIAS, null) as java.security.PrivateKey
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(value.toByteArray(StandardCharsets.UTF_8))
            Base64.encodeToString(sign(), Base64.NO_WRAP)
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun JSONObject.toState(activated: Boolean): GiftAccessState = GiftAccessState(
        activated = activated,
        deviceCount = optInt("device_count", 0),
        maxDevices = optInt("max_devices", 3),
        usedKrw = optInt("used_krw", 0),
        remainingKrw = optInt("remaining_krw", 3_000),
        month = optString("month"),
        nextReset = optString("next_reset"),
    )

    companion object {
        private const val PREFS = "gift_access"
        private const val KEY_LICENSE_ID = "license_id"
        private const val KEY_ALIAS = "ai_figure_paint_gift_device_key"
    }
}
