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
            // Endpoint OpenAI-compatible (unico que soporta gemini-2.5-flash-image en v1beta)
            // Docs: https://ai.google.dev/gemini-api/docs/openai#generate-image
            try {
                val url = "https://generativelanguage.googleapis.com/v1beta/openai/images/generations"
                val json = gson.toJson(mapOf(
                    "model" to "gemini-2.5-flash-image",
                    "prompt" to prompt,
                    "response_format" to "b64_json",
                    "n" to 1
                ))
                val req = Request.Builder().url(url)
                    .post(json.toRequestBody("application/json".toMediaType()))
                    .addHeader("Authorization", "Bearer $apiKey")
                    .build()
                val response = httpClient.newCall(req).execute()
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val parsed = com.google.gson.JsonParser.parseString(body).asJsonObject
                    val dataArray = parsed.getAsJsonArray("data")
                    if (dataArray != null && dataArray.size() > 0) {
                        val b64 = dataArray[0].asJsonObject?.get("b64_json")?.asString
                        if (!b64.isNullOrBlank()) return@withContext b64
                        // Algunas respuestas usan url en vez de b64_json
                        val imgUrl = dataArray[0].asJsonObject?.get("url")?.asString
                        if (!imgUrl.isNullOrBlank()) {
                            // Descargar la imagen y convertir a base64
                            try {
                                val imgReq = Request.Builder().url(imgUrl).build()
                                httpClient.newCall(imgReq).execute().use { imgResp ->
                                    val bytes = imgResp.body?.bytes()
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        return@withContext android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    "ERROR:La API no devolvió una imagen (respuesta: ${body.take(200)})"
                } else {
                    val errMsg = try {
                        com.google.gson.JsonParser.parseString(body).asJsonObject
                            .getAsJsonObject("error")?.get("message")?.asString
                    } catch (_: Exception) { null }
                    "ERROR:HTTP ${response.code}: ${errMsg ?: response.message}"
                }
            } catch (e: Exception) {
                "ERROR:${e.javaClass.simpleName}: ${e.message ?: "Excepción"}"
            }
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
