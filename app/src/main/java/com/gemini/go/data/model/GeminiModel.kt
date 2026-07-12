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
            GeminiModel("gemini-3.1-flash-lite-image", "Nano Banana 2 Lite", "Modelo de imagen más rápido y eficiente — tier gratis"),
            GeminiModel("gemini-3.1-flash-image", "Nano Banana 2", "Modelo de imagen versátil y equilibrado — tier gratis"),
            GeminiModel("gemini-3-pro-image", "Nano Banana Pro", "Modelo de imagen premium para tareas complejas — tier gratis"),
            GeminiModel("gemini-2.5-flash-image", "Gemini 2.5 Flash Image", "Nano Banana legacy — deprecado, usa Nano Banana 2")
        )
        val DEFAULT = PRESETS.first { it.id == "gemini-3.1-flash-lite" }
        fun fromId(id: String?): GeminiModel {
            if (id.isNullOrBlank()) return DEFAULT
            return PRESETS.firstOrNull { it.id == id } ?: GeminiModel(id, id, "Modelo personalizado", isCustom = true)
        }
    }
}
