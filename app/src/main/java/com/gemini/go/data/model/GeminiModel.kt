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
            GeminiModel("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", "Modelo de texto gratis — 18 RPD / 500 TPD, ideal para Android Go"),
            GeminiModel("gemini-3-flash-live", "Gemini 3 Flash Live", "Voz tiempo real gratis — 83 RPD, TPM ilimitado"),
            GeminiModel("imagen-4.0-fast-generate-001", "Imagen 4 Fast", "Genera imágenes rápido — requiere billing"),
            GeminiModel("imagen-4.0-generate-001", "Imagen 4", "Genera imágenes calidad alta — requiere billing"),
            GeminiModel("imagen-4.0-ultra-generate-001", "Imagen 4 Ultra", "Máxima calidad imagen — requiere billing")
        )
        val DEFAULT = PRESETS.first { it.id == "gemini-3.1-flash-lite" }
        fun fromId(id: String?): GeminiModel {
            if (id.isNullOrBlank()) return DEFAULT
            return PRESETS.firstOrNull { it.id == id } ?: GeminiModel(id, id, "Modelo personalizado", isCustom = true)
        }
    }
}
    }
}
    }
}
