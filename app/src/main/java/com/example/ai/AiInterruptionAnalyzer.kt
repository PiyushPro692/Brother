package com.example.ai

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiAnalysisResult(
    val isInterruption: Boolean,
    val confidence: Float,
    val reason: String,
    val suggestedAction: String,
    val isDismissable: Boolean = false
)

object AiInterruptionAnalyzer {

    private const val TAG = "AiInterruptionAnalyzer"
    private const val MODEL_NAME = "gemini-3.8-flash"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Fast local heuristic analysis using accessibility node hierarchies
     */
    fun fastHeuristicCheck(screenTexts: List<String>): AiAnalysisResult {
        val lowerTexts = screenTexts.map { it.lowercase().trim() }

        val strongAdTokens = listOf(
            "advertisement", "sponsored", "install now", "download app",
            "reward granted in", "reward in", "skip in", "play store", "free trial"
        )
        val dismissTokens = listOf("close", "✕", "x", "skip ad", "dismiss", "no thanks", "not now")

        var matchedAdToken: String? = null
        for (text in lowerTexts) {
            for (token in strongAdTokens) {
                if (text == token || text.contains(token)) {
                    matchedAdToken = token
                    break
                }
            }
            if (matchedAdToken != null) break
        }

        val hasDismiss = lowerTexts.any { text ->
            dismissTokens.any { d -> text == d || text.contains(d) }
        }

        return if (matchedAdToken != null) {
            AiAnalysisResult(
                isInterruption = true,
                confidence = 0.92f,
                reason = "Heuristic detected '$matchedAdToken' in screen elements",
                suggestedAction = if (hasDismiss) "Wait for countdown or dismiss when ready" else "Pause automation and wait for ad completion",
                isDismissable = hasDismiss
            )
        } else {
            AiAnalysisResult(
                isInterruption = false,
                confidence = 0.85f,
                reason = "No ad keywords found in visible hierarchy",
                suggestedAction = "Proceed normally",
                isDismissable = false
            )
        }
    }

    /**
     * Advanced AI evaluation using Gemini 3.8-Flash
     */
    suspend fun analyzeWithGemini(screenTexts: List<String>): AiAnalysisResult = withContext(Dispatchers.IO) {
        // First run heuristic; if already high confidence or no API key, return fast result
        val heuristic = fastHeuristicCheck(screenTexts)

        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Exception) {
            ""
        }

        if (apiKey.isNullOrBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.d(TAG, "Gemini API key not configured, using reliable local accessibility heuristics")
            return@withContext heuristic
        }

        try {
            val prompt = """
                You are an AI automation watchdog for an Android auto-clicker.
                Analyze the following screen element texts captured from the active application:
                ${screenTexts.take(30).joinToString("\n") { "- $it" }}

                Determine if this screen is currently showing an advertisement, full-screen promotional takeover, unexpected interruption, or system modal dialogue that requires pausing automated taps.
                
                Respond ONLY with a JSON object in this format:
                {
                  "is_interruption": true/false,
                  "confidence": 0.0 to 1.0,
                  "reason": "Brief explanation",
                  "suggested_action": "pause_and_wait" | "try_dismiss" | "proceed",
                  "has_dismiss_button": true/false
                }
            """.trimIndent()

            val requestJson = JSONObject().apply {
                val contentsArr = JSONArray().apply {
                    val partObj = JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    }
                    put(partObj)
                }
                put("contents", contentsArr)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("responseMimeType", "application/json")
                })
            }

            val request = Request.Builder()
                .url("$BASE_URL?key=$apiKey")
                .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val bodyStr = response.body?.string() ?: ""
                val rootObj = JSONObject(bodyStr)
                val candidates = rootObj.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val textOutput = firstCandidate?.optJSONObject("content")
                    ?.optJSONArray("parts")?.optJSONObject(0)?.optString("text")

                if (!textOutput.isNullOrBlank()) {
                    val parsed = JSONObject(textOutput)
                    return@withContext AiAnalysisResult(
                        isInterruption = parsed.optBoolean("is_interruption", heuristic.isInterruption),
                        confidence = parsed.optDouble("confidence", 0.9).toFloat(),
                        reason = parsed.optString("reason", heuristic.reason),
                        suggestedAction = parsed.optString("suggested_action", heuristic.suggestedAction),
                        isDismissable = parsed.optBoolean("has_dismiss_button", heuristic.isDismissable)
                    )
                }
            }
            heuristic
        } catch (e: Exception) {
            Log.w(TAG, "Gemini AI analysis error, falling back to heuristics: ${e.message}")
            heuristic
        }
    }
}
