package com.gemini.go.data.model

data class GeminiModel(
    val id: String,
    val displayName: String,
    val description: String,
    val isCustom: Boolean = false,
    val supportsImageOutput: Boolean = false
) {
    companion object {
        val PRESETS = listOf(
            GeminiModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", "Última generación, ultrarrápido y eficiente — ideal para Android Go"),
            GeminiModel("gemini-2.5-flash", "Gemini 2.5 Flash", "Rápido y multimodal, bueno para la mayoría de tareas"),
            GeminiModel("gemini-2.5-pro", "Gemini 2.5 Pro", "Más potente para razonamiento complejo"),
            GeminiModel("gemini-2.0-flash", "Gemini 2.0 Flash", "Generación anterior, rápido"),
            GeminiModel("imagen-4.0-fast-generate-001", "Imagen 4 Fast", "Genera imágenes rápido — 25/día gratis", supportsImageOutput = true),
            GeminiModel("imagen-4.0-generate-001", "Imagen 4", "Genera imágenes de alta calidad — 25/día gratis", supportsImageOutput = true),
            GeminiModel("imagen-4.0-ultra-generate-001", "Imagen 4 Ultra", "Máxima calidad de imagen — 25/día gratis", supportsImageOutput = true))
        )
        val DEFAULT = PRESETS.first { it.id == "gemini-3.1-flash-lite" }
        fun fromId(id: String?): GeminiModel {
            if (id.isNullOrBlank()) return DEFAULT
            return PRESETS.firstOrNull { it.id == id } ?: GeminiModel(id, id, "Modelo personalizado", isCustom = true)
        }
    }
}
