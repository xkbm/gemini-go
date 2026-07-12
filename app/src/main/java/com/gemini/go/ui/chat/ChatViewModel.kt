package com.gemini.go.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.gemini.go.GeminiApp
import com.gemini.go.data.api.StreamEvent
import com.gemini.go.data.model.ConversationEntity
import com.gemini.go.data.model.GeminiModel
import com.gemini.go.data.model.MessageEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GeminiApp).repository
    private val prefs = (app as GeminiApp).prefs
    private val _messages = MutableLiveData<List<MessageUiModel>>(emptyList())
    val messages: LiveData<List<MessageUiModel>> = _messages
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error
    private val _currentConversation = MutableLiveData<ConversationEntity?>()
    val currentConversation: LiveData<ConversationEntity?> = _currentConversation
    private var streamJob: Job? = null
    private var messagesObserverJob: Job? = null
    val hasApiKey: Boolean get() = prefs.apiKey.isNotBlank()
    val currentModel: GeminiModel get() = prefs.model

    fun loadOrCreateConversation(conversationId: String?) {
        viewModelScope.launch {
            val conv = if (conversationId != null) repo.getConversation(conversationId) else null
            if (conv != null) { _currentConversation.value = conv; observeMessages(conv.id) }
            else { _currentConversation.value = null; _messages.value = emptyList() }
        }
    }
    private fun observeMessages(conversationId: String) {
        messagesObserverJob?.cancel()
        messagesObserverJob = viewModelScope.launch { repo.observeMessages(conversationId).collectLatest { _messages.value = it.map { MessageUiModel.from(it) } } }
    }
    fun sendMessage(text: String, imageUris: List<String>) {
        if (!hasApiKey) { _error.value = getApplication<GeminiApp>().getString(com.gemini.go.R.string.error_no_api_key); return }
        if (text.isBlank() && imageUris.isEmpty()) return
        viewModelScope.launch {
            var conv = _currentConversation.value
            if (conv == null) { conv = repo.createConversation(prefs.model); _currentConversation.value = conv; observeMessages(conv.id) }
            if (conv.title == "Nuevo chat" && text.isNotBlank()) {
                val title = text.take(40).replace("\n", " ").trim()
                repo.renameConversation(conv.id, if (text.length > 40) "$title…" else title)
            }
            val userMsg = MessageEntity(UUID.randomUUID().toString(), conv.id, "user", text, imageUris.joinToString(","), System.currentTimeMillis(), false)
            repo.saveMessage(userMsg)
            val assistantMsg = MessageEntity(UUID.randomUUID().toString(), conv.id, "model", "", "", System.currentTimeMillis() + 1, true)
            repo.saveMessage(assistantMsg)
            startStreaming(conv.id, assistantMsg)
        }
    }
    fun regenerateLast() {
        if (_isGenerating.value) return
        if (!hasApiKey) { _error.value = getApplication<GeminiApp>().getString(com.gemini.go.R.string.error_no_api_key); return }
        val conv = _currentConversation.value ?: return
        viewModelScope.launch {
            val current = _messages.value.orEmpty()
            val lastAssistant = current.lastOrNull { it.role == "model" && !it.isLoading } ?: return@launch
            repo.deleteMessage(lastAssistant.id)
            val assistantMsg = MessageEntity(UUID.randomUUID().toString(), conv.id, "model", "", "", System.currentTimeMillis(), true)
            repo.saveMessage(assistantMsg)
            startStreaming(conv.id, assistantMsg)
        }
    }
    private fun startStreaming(conversationId: String, assistantMsg: MessageEntity) {
        streamJob?.cancel()
        _isGenerating.value = true
        streamJob = viewModelScope.launch {
            val model = prefs.model; val systemPrompt = prefs.systemPrompt; val temperature = prefs.temperature
            try {
                repo.sendMessage(conversationId, model, systemPrompt, temperature).collect { event ->
                    when (event) {
                        is StreamEvent.Chunk -> { assistantMsg.text += event.text; repo.updateMessage(assistantMsg) }
                        is StreamEvent.Image -> { assistantMsg.generatedImageBase64 = event.base64; assistantMsg.generatedImageMime = event.mimeType; repo.updateMessage(assistantMsg) }
                        is StreamEvent.FunctionCall -> {
                            val fnName = event.name.lowercase()
                            if (fnName.contains("image") || fnName.contains("generate") || fnName.contains("imagen") || fnName.contains("draw") || fnName.contains("dalle") || fnName.contains("text2im")) {
                                val promptArg = event.args.entries.firstOrNull { it.key.lowercase().contains("prompt") }?.value?.toString()
                                if (promptArg != null) {
                                    viewModelScope.launch {
                                        try {
                                            assistantMsg.text = "🎨 Generando imagen…\n\n$promptArg"; repo.updateMessage(assistantMsg)
                                            val base64 = repo.generateImage(promptArg)
                                            if (base64 != null && !base64.startsWith("ERROR:")) {
                                                assistantMsg.generatedImageBase64 = base64; assistantMsg.generatedImageMime = "image/png"; assistantMsg.text = "🎨 $promptArg"; repo.updateMessage(assistantMsg)
                                            } else if (base64 != null && base64.startsWith("ERROR:")) {
                                                val errMsg = base64.removePrefix("ERROR:")
                                                assistantMsg.text = "⚠️ No se pudo generar la imagen.\n\nError: $errMsg\n\nPrompt: $promptArg"; repo.updateMessage(assistantMsg)
                                            } else {
                                                assistantMsg.text = "⚠️ No se pudo generar la imagen.\n\nPrompt: $promptArg"; repo.updateMessage(assistantMsg)
                                            }
                                        } catch (e: Exception) { assistantMsg.text = "⚠️ Error: ${e.message}"; repo.updateMessage(assistantMsg) }
                                    }
                                } else { assistantMsg.text = "🎨 Generando imagen…"; repo.updateMessage(assistantMsg) }
                            }
                        }
                        is StreamEvent.Error -> { assistantMsg.text = "⚠️ ${event.message}"; assistantMsg.isStreaming = false; repo.updateMessage(assistantMsg); _error.value = event.message }
                        is StreamEvent.Done -> { assistantMsg.isStreaming = false; repo.updateMessage(assistantMsg) }
                    }
                }
            } catch (e: Exception) { assistantMsg.text = "⚠️ ${e.message ?: "Error"}"; assistantMsg.isStreaming = false; repo.updateMessage(assistantMsg); _error.value = e.message }
            finally { _isGenerating.value = false }
        }
    }
    fun stopGenerating() {
        streamJob?.cancel(); _isGenerating.value = false
        val current = _messages.value.orEmpty()
        current.filter { it.isStreaming }.forEach { msg -> viewModelScope.launch { repo.markMessageStopped(msg.id) } }
    }
    fun errorShown() { _error.value = null }
    fun startNewChat() { messagesObserverJob?.cancel(); streamJob?.cancel(); _isGenerating.value = false; _currentConversation.value = null; _messages.value = emptyList() }
    override fun onCleared() { super.onCleared(); streamJob?.cancel(); messagesObserverJob?.cancel() }
}
