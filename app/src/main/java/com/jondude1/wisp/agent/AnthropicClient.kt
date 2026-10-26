package com.jondude1.wisp.agent

import com.jondude1.wisp.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiError(val code: Int, message: String) : Exception(message)

/** Minimal Messages API client. Non-streaming for now; streaming is on the roadmap. */
class AnthropicClient(private val settings: Settings) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .build()

    suspend fun send(system: String, messages: List<JSONObject>, tools: JSONArray): JSONObject =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", settings.model)
                .put("max_tokens", 2048)
                .put("system", system)
                .put("tools", tools)
                .put("messages", JSONArray(messages))

            val req = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", settings.apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val msg = runCatching {
                        JSONObject(text).getJSONObject("error").getString("message")
                    }.getOrDefault(text.take(200))
                    throw ApiError(resp.code, friendly(resp.code, msg))
                }
                JSONObject(text)
            }
        }

    private fun friendly(code: Int, msg: String): String = when (code) {
        401 -> "The API key was rejected. Check it in Settings."
        404 -> "The model '${settings.model}' wasn't found. Check the model name in Settings."
        429 -> "Rate limited. Wait a moment and try again."
        529 -> "The API is overloaded. Try again shortly."
        else -> "API error $code: $msg"
    }
}
