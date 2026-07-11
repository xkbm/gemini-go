package com.gemini.go.ui.conversations

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.gemini.go.GeminiApp
import com.gemini.go.R
import com.gemini.go.data.model.ConversationEntity
import com.gemini.go.databinding.ActivityConversationsBinding
import com.gemini.go.ui.chat.ChatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ConversationsViewModel(app: android.app.Application) : AndroidViewModel(app) {
    private val repo = (app as GeminiApp).repository
    val conversations = repo.observeConversations().asLiveData()
    fun delete(conversation: ConversationEntity, onDone: () -> Unit) { viewModelScope.launch { repo.deleteConversation(conversation.id); onDone() } }
}

class ConversationsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConversationsBinding
    private val viewModel: ConversationsViewModel by viewModels()
    private val adapter = ConversationsAdapter(
        onClick = { conv -> startActivity(Intent(this, ChatActivity::class.java).apply { putExtra(ChatActivity.EXTRA_CONVERSATION_ID, conv.id) }); finish() },
        onDelete = { conv -> confirmDelete(conv) }
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConversationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.fabNewChat.setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent); finish()
        }
        binding.conversationsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.conversationsRecyclerView.adapter = adapter
        viewModel.conversations.observe(this) { list ->
            adapter.submitList(list)
            binding.emptyState.visibility = if (list.isNullOrEmpty()) View.VISIBLE else View.GONE
            binding.conversationsRecyclerView.visibility = if (list.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
    }
    private fun confirmDelete(conv: ConversationEntity) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_conversation_confirm).setMessage(conv.title)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewModel.delete(conv) {} }
            .show()
    }
}
