package com.gemini.go.data.repo

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.gemini.go.data.api.GeminiClient
import com.gemini.go.data.api.StreamEvent
import com.gemini.go.data.db.GeminiDatabase
import com.gemini.go.data.model.Content
import com.gemini.go.data.model.ConversationEntity
import com.gemini.go.data.model.GenerationConfig
import com.gemini.go.data.model.GeminiModel
import com.gemini.go.data.model.MessageEntity
import com.gemini.go.data.model.Part
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class GeminiRepository(private val context: Context, private val db: GeminiDatabase, private val prefs: PreferencesManager) {
    private val client by lazy { GeminiClient(prefs.apiKey) }
    fun observeConversations(): Flow<List<ConversationEntity>> = db.conversationDao().observeAll()
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> = db.messageDao().observeByConversation(conversationId)
    suspend fun getConversation(id: String): ConversationEntity? = db.conversationDao().getById(id)
    suspend fun createConversation(model: GeminiModel): ConversationEntity {
        val now = System.currentTimeMillis()
        val conv = ConversationEntity(UUID.randomUUID().toString(), "Nuevo chat", now, now, model.id)
        db.conversationDao().upsert(conv)
        prefs.lastConversationId = conv.id
        return conv
    }
    suspend fun renameConversation(id: String, title: String) {
        db.conversationDao().getById(id)?.let { db.conversationDao().update(it.copy(title = title, updatedAt = System.currentTimeMillis())) }
    }
    suspend fun deleteConversation(id: String) {
        db.conversationDao().getById(id)?.let { db.conversationDao().delete(it); if (prefs.lastConversationId == id) prefs.lastConversationId = null }
    }
    suspend fun clearAllConversations() { db.conversationDao().deleteAll(); prefs.lastConversationId = null }
    suspend fun saveMessage(message: MessageEntity) {
        db.messageDao().insert(message)
        db.conversationDao().getById(message.conversationId)?.let { db.conversationDao().update(it.copy(updatedAt = System.currentTimeMillis())) }
    }
    suspend fun updateMessage(message: MessageEntity) = db.messageDao().update(message)
    suspend fun deleteMessage(id: String) = db.messageDao().deleteById(id)
    suspend fun markMessageStopped(id: String) = db.messageDao().markStopped(id)

    suspend fun getMessagesForApi(conversationId: String): List<Content> {
        val msgs = db.messageDao().getByConversation(conversationId)
        return msgs.filter { it.text.isNotBlank() || it.imagePaths.isNotBlank() }.map { msg ->
            val parts = mutableListOf<Part>()
            if (msg.imagePaths.isNotBlank()) {
                msg.imagePaths.split(",").filter { it.isNotBlank() }.forEach { path ->
                    try {
                        val (mime, base64) = readImageAsBase64(Uri.parse(path))
                        if (base64.isNotEmpty()) parts.add(Part.InlineDataPart(mime, base64))
                    } catch (_: Exception) {}
                }
            }
            if (msg.text.isNotBlank()) parts.add(Part.TextPart(msg.text))
            Content(msg.role, parts)
        }
    }

    fun sendMessage(conversationId: String, model: GeminiModel, systemPrompt: String, temperature: Float): Flow<StreamEvent> = flow {
        val contents = getMessagesForApi(conversationId)
        if (contents.isEmpty()) { emit(StreamEvent.Error("No hay mensajes para enviar")); return@flow }
        val responseModalities = if (model.supportsImageOutput) listOf("TEXT", "IMAGE") else null
        client.streamGenerate(model, contents, systemPrompt, GenerationConfig(temperature = temperature.toDouble(), responseModalities = responseModalities)).collect { emit(it) }
    }

    fun readImageAsBase64(uri: Uri): Pair<String, String> {
        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("No se pudo leer la imagen")
        return mime to Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    suspend fun generateImage(prompt: String): String? {
        return try {
            client.generateImage(prompt)
        } catch (e: Exception) {
            "ERROR:Excepción: ${e.javaClass.simpleName}: ${e.message ?: "(sin mensaje)"}"
        }
    }

    // Export functionality
    suspend fun exportConversation(conversationId: String, format: ExportFormat): String {
        val conv = db.conversationDao().getById(conversationId)
        val messages = db.messageDao().getByConversation(conversationId)
        return when (format) {
            ExportFormat.TXT -> exportToTxt(conv, messages)
            ExportFormat.JSON -> exportToJson(conv, messages)
        }
    }

    private fun exportToTxt(conv: ConversationEntity?, messages: List<MessageEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("Conversación: ${conv?.title ?: "Sin título"}")
        sb.appendLine("Fecha: ${conv?.createdAt?.let { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "Desconocida"}")
        sb.appendLine("Modelo: ${conv?.modelId ?: "Desconocido"}")
        sb.appendLine("Mensajes: ${messages.size}")
        sb.appendLine("".padEnd(50, '='))
        sb.appendLine()
        for (msg in messages) {
            val role = if (msg.role == "user") "Tú" else "Gemini"
            val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
            sb.appendLine("[$time] $role:")
            sb.appendLine(msg.text)
            if (msg.imagePaths.isNotBlank()) sb.appendLine("[Imagen adjunta: ${msg.imagePaths}]")
            if (msg.generatedImageBase64.isNotBlank()) sb.appendLine("[Imagen generada]")
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun exportToJson(conv: ConversationEntity?, messages: List<MessageEntity>): String {
        val map = mutableMapOf<String, Any>()
        map["conversation"] = mapOf(
            "id" to conv?.id,
            "title" to conv?.title,
            "modelId" to conv?.modelId,
            "createdAt" to conv?.createdAt,
            "updatedAt" to conv?.updatedAt
        )
        map["messages"] = messages.map { msg ->
            mapOf(
                "id" to msg.id,
                "role" to msg.role,
                "text" to msg.text,
                "imagePaths" to msg.imagePaths,
                "timestamp" to msg.timestamp,
                "isStreaming" to msg.isStreaming,
                "generatedImageBase64" to if (msg.generatedImageBase64.isNotBlank()) "[base64 image]" else ""
            )
        }
        return org.json.JSONObject(map).toString(2)
    }

    enum class ExportFormat { TXT, JSON }
}
