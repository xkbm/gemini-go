package com.gemini.go.data.model

import com.google.gson.annotations.SerializedName

sealed class Part {
    data class TextPart(val text: String) : Part()
    data class InlineDataPart(val mimeType: String, val data: String) : Part()
    data class FunctionCallPart(val name: String, val args: Map<String, Any?>) : Part()
}

data class Content(@SerializedName("role") val role: String, @SerializedName("parts") val parts: List<Part>)
data class GenerationConfig(
    @SerializedName("temperature") val temperature: Double = 0.9,
    @SerializedName("topP") val topP: Double = 0.95, @SerializedName("topK") val topK: Int = 40,
    @SerializedName("maxOutputTokens") val maxOutputTokens: Int = 8192,
    @SerializedName("responseModalities") val responseModalities: List<String>? = null
)
data class GenerateRequest(
    @SerializedName("contents") val contents: List<Content>,
    @SerializedName("generationConfig") val generationConfig: GenerationConfig = GenerationConfig(),
    @SerializedName("systemInstruction") val systemInstruction: Content? = null
)
data class StreamResponse(@SerializedName("candidates") val candidates: List<Candidate>?) {
    fun text(): String? = candidates?.firstOrNull()?.content?.parts?.filterIsInstance<Part.TextPart>()?.joinToString("") { it.text }
    fun imageBase64(): Pair<String, String>? = candidates?.firstOrNull()?.content?.parts?.filterIsInstance<Part.InlineDataPart>()?.firstOrNull { it.mimeType.startsWith("image/") }?.let { it.mimeType to it.data }
    fun functionCall(): Part.FunctionCallPart? = candidates?.firstOrNull()?.content?.parts?.filterIsInstance<Part.FunctionCallPart>()?.firstOrNull()
}
data class Candidate(@SerializedName("content") val content: Content?, @SerializedName("finishReason") val finishReason: String?)
data class ErrorResponse(@SerializedName("error") val error: ErrorDetail?)
data class ErrorDetail(@SerializedName("code") val code: Int, @SerializedName("message") val message: String, @SerializedName("status") val status: String?)
