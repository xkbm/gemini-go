package com.gemini.go.data.api

import com.gemini.go.data.model.Content
import com.gemini.go.data.model.ErrorResponse
import com.gemini.go.data.model.GenerateRequest
import com.gemini.go.data.model.GenerationConfig
import com.gemini.go.data.model.GeminiModel
import com.gemini.go.data.model.Part
import com.gemini.go.data.model.StreamResponse
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class GeminiClient(private val apiKey: String, private val httpClient: OkHttpClient = defaultClient()) {
    private val gson = GsonBuilder().registerTypeAdapter(Part::class.java, PartAdapter()).create()

    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
    }

    fun streamGenerate(model: GeminiModel, contents: List<Content>, systemPrompt: String?, config: GenerationConfig): Flow<StreamEvent> = callbackFlow {
        val systemInstruction = systemPrompt?.takeIf { it.isNotBlank() }?.let { Content("user", listOf(Part.TextPart(it))) }
        val request = GenerateRequest(contents, config, systemInstruction)
        val json = gson.toJson(request)
        val url = "$BASE_URL/${model.id}:streamGenerateContent?alt=sse&key=$apiKey"
        val req = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())).build()
        var call: okhttp3.Call? = null
        var reader: BufferedReader? = null
        try {
            call = httpClient.newCall(req)
            val response = call.execute()
            if (!response.isSuccessful) {
                val errBody = response.body?.string() ?: ""
                val msg = try { gson.fromJson(errBody, ErrorResponse::class.java)?.error?.message ?: "HTTP ${response.code}" } catch (_: Exception) { "HTTP ${response.code}" }
                trySend(StreamEvent.Error(msg)); close(); return@callbackFlow
            }
            reader = BufferedReader(InputStreamReader(response.body!!.byteStream()))
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (isClosedForSend) break
                val l = line ?: continue
                if (l.startsWith("data:")) {
                    val data = l.removePrefix("data:").trim()
                    if (data.isEmpty() || data == "[DONE]") continue
                    sb.append(data)
                    try {
                        val chunk = gson.fromJson(sb.toString(), StreamResponse::class.java)
                        chunk.text()?.let { if (it.isNotEmpty()) trySend(StreamEvent.Chunk(it)) }
                        chunk.imageBase64()?.let { trySend(StreamEvent.Image(it.first, it.second)) }
                        chunk.functionCall()?.let { trySend(StreamEvent.FunctionCall(it.name, it.args)) }
                    } catch (_: Exception) {}
                    sb.setLength(0)
                } else if (l.isEmpty()) {
                    if (sb.isNotEmpty()) {
                        try {
                            val chunk = gson.fromJson(sb.toString(), StreamResponse::class.java)
                            chunk.text()?.let { if (it.isNotEmpty()) trySend(StreamEvent.Chunk(it)) }
                            chunk.imageBase64()?.let { trySend(StreamEvent.Image(it.first, it.second)) }
                            chunk.functionCall()?.let { trySend(StreamEvent.FunctionCall(it.name, it.args)) }
                        } catch (_: Exception) {}
                        sb.setLength(0)
                    }
                }
            }
            trySend(StreamEvent.Done); close()
        } catch (e: IOException) { trySend(StreamEvent.Error(e.message ?: "Network error")); close()
        } catch (e: Exception) { trySend(StreamEvent.Error(e.message ?: "Unknown error")); close()
        } finally { try { reader?.close() } catch (_: Exception) {} }
        awaitClose { call?.cancel(); try { reader?.close() } catch (_: Exception) {} }
    }.flowOn(Dispatchers.IO)

    /**
     * Generates an image using Gemini's native image generation.
     * Tries multiple model names in order — the FREE tier model (gemini-2.5-flash-image,
     * a.k.a. "Nano Banana") is tried first since it's available on the free tier
     * (500 requests/day).
     * Returns base64 image data on success, or an error message prefixed with "ERROR:".
     *
     * CRITICAL: Must run on IO dispatcher — network calls on main thread throw
     * NetworkOnMainThreadException.
     */
    suspend fun generateImage(prompt: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            // --- Ruta 1: Imagen 4 (Gemini, requiere billing) ---
            val models = listOf(
                "imagen-4.0-fast-generate-001",
                "imagen-4.0-generate-001",
                "imagen-4.0-ultra-generate-001"
            )
            val errors = mutableListOf<String>()
            for (model in models) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:predict?key=$apiKey"
                    val json = gson.toJson(mapOf(
                        "instances" to listOf(mapOf("prompt" to prompt)),
                        "parameters" to mapOf("sampleCount" to 1)
                    ))
                    val req = Request.Builder().url(url)
                        .post(json.toRequestBody("application/json".toMediaType()))
                        .build()
                    val response = httpClient.newCall(req).execute()
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        val parsed = com.google.gson.JsonParser.parseString(body).asJsonObject
                        val predictions = parsed.getAsJsonArray("predictions")
                        if (predictions != null && predictions.size() > 0) {
                            val prediction = predictions[0].asJsonObject
                            val b64 = prediction.get("bytesBase64Encoded")?.asString
                            if (!b64.isNullOrBlank()) return@withContext b64
                        }
                        errors.add("$model: sin imagen (resp: ${body.take(100)})")
                    } else {
                        val errMsg = try {
                            com.google.gson.JsonParser.parseString(body).asJsonObject
                                .getAsJsonObject("error")?.get("message")?.asString
                        } catch (_: Exception) { null }
                        errors.add("$model: HTTP ${response.code}: ${errMsg ?: response.message}")
                        if (response.code == 400) break
                    }
                } catch (e: Exception) {
                    errors.add("$model: ${e.javaClass.simpleName}: ${e.message ?: "Excepción"}")
                }
            }

            // --- Ruta 2: Pollinations.ai (gratis, sin API key) ---
            try {
                val sanitized = prompt.replace(" ", "-")
                    .replace(Regex("[^a-zA-Z0-9\\-_]"), "")
                    .take(200)
                val url = "https://image.pollinations.ai/prompt/$sanitized?model=flux"
                val req = Request.Builder().url(url).get().build()
                val response = httpClient.newCall(req).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        return@withContext "POLLINATIONS:$b64"
                    }
                }
                errors.add("pollinations.ai: HTTP ${response.code}")
            } catch (e: Exception) {
                errors.add("pollinations.ai: ${e.javaClass.simpleName}: ${e.message ?: "Excepción"}")
            }

            "ERROR:Todos los modelos fallaron:\n${errors.joinToString("\n")}"
        }
    }
}

sealed class StreamEvent {
    data class Chunk(val text: String) : StreamEvent()
    data class Image(val mimeType: String, val base64: String) : StreamEvent()
    data class FunctionCall(val name: String, val args: Map<String, Any?>) : StreamEvent()
    data class Error(val message: String) : StreamEvent()
    object Done : StreamEvent()
}
