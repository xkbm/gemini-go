package com.gemini.go.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.gemini.go.GeminiApp
import com.gemini.go.R
import com.gemini.go.databinding.ActivityChatBinding
import com.gemini.go.ui.conversations.ConversationsActivity
import com.gemini.go.ui.settings.SettingsActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ChatActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private val adapter = MessagesAdapter(
        onCopy = { copyToClipboard(it) }, onRegenerate = { viewModel.regenerateLast() }, onShare = { shareText(it) }
    )
    private val attachedImages = mutableListOf<String>()
    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris != null) {
            uris.forEach { uri -> val str = uri.toString(); if (attachedImages.size < 4 && str !in attachedImages) attachedImages.add(str) }
            refreshAttachmentPreviews()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { startActivity(Intent(this, ConversationsActivity::class.java)) }
        setupRecyclerView(); setupInputBar(); setupWelcomeChips(); observeViewModel()
        val conversationId = intent.getStringExtra(EXTRA_CONVERSATION_ID)
        if (conversationId != null) viewModel.loadOrCreateConversation(conversationId)
        else { val lastId = (application as GeminiApp).prefs.lastConversationId; if (lastId != null) viewModel.loadOrCreateConversation(lastId) }
    }
    private fun setupRecyclerView() {
        binding.messagesRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messagesRecyclerView.adapter = adapter
        binding.messagesRecyclerView.itemAnimator?.changeDuration = 0
    }
    private fun setupInputBar() {
        binding.editMessage.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { binding.btnSend.isEnabled = !s.isNullOrBlank() || attachedImages.isNotEmpty() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.btnSend.setOnClickListener {
            if (viewModel.isGenerating.value) viewModel.stopGenerating()
            else {
                val text = binding.editMessage.text.toString().trim()
                if (text.isBlank() && attachedImages.isEmpty()) return@setOnClickListener
                val images = attachedImages.toList()
                viewModel.sendMessage(text, images)
                binding.editMessage.text?.clear(); attachedImages.clear(); refreshAttachmentPreviews()
            }
        }
        binding.btnAttach.setOnClickListener { pickImageLauncher.launch("image/*") }
        binding.btnSend.isEnabled = false
    }
    private fun setupWelcomeChips() {
        val chips = listOf(binding.chipPrompt1, binding.chipPrompt2, binding.chipPrompt3, binding.chipPrompt4)
        chips.forEach { chip -> chip.setOnClickListener { binding.editMessage.setText(chip.text); val len = binding.editMessage.text?.length ?: 0; binding.editMessage.setSelection(len) } }
    }
    private fun observeViewModel() {
        viewModel.messages.observe(this) { messages ->
            adapter.submitList(messages.toList()) { if (messages.isNotEmpty()) binding.messagesRecyclerView.scrollToPosition(messages.lastIndex) }
            binding.welcomeLayout.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
            binding.messagesRecyclerView.visibility = if (messages.isEmpty()) View.GONE else View.VISIBLE
        }
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.isGenerating.collect { generating -> binding.btnSend.setIconResource(if (generating) R.drawable.ic_stop else R.drawable.ic_send) } } }
        viewModel.error.observe(this) { err ->
            if (err != null) {
                Snackbar.make(binding.root, err, Snackbar.LENGTH_LONG).setAction(getString(R.string.action_settings)) { startActivity(Intent(this, SettingsActivity::class.java)) }.show()
                viewModel.errorShown()
            }
        }
        viewModel.currentConversation.observe(this) { conv -> binding.toolbar.title = conv?.title?.takeIf { it.isNotBlank() } ?: getString(R.string.app_name) }
    }
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean { menuInflater.inflate(R.menu.chat_menu, menu); return true }
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_new_chat -> { viewModel.startNewChat(); binding.welcomeLayout.visibility = View.VISIBLE; binding.messagesRecyclerView.visibility = View.GONE }
            R.id.action_conversations -> startActivity(Intent(this, ConversationsActivity::class.java))
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
        }
        return true
    }
    private fun refreshAttachmentPreviews() {
        binding.attachmentContainer.removeAllViews()
        if (attachedImages.isEmpty()) { binding.attachmentScroll.visibility = View.GONE; return }
        binding.attachmentScroll.visibility = View.VISIBLE
        attachedImages.forEach { uriStr ->
            val view = layoutInflater.inflate(R.layout.item_attachment_preview, binding.attachmentContainer, false)
            val img = view.findViewById<ImageView>(R.id.imageThumb)
            val btnRemove = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnRemove)
            Glide.with(this).load(Uri.parse(uriStr)).centerCrop().into(img)
            btnRemove.setOnClickListener { attachedImages.remove(uriStr); refreshAttachmentPreviews() }
            binding.attachmentContainer.addView(view)
        }
    }
    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Gemini", text))
        Snackbar.make(binding.root, R.string.copied, Snackbar.LENGTH_SHORT).show()
    }
    private fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }
    companion object { const val EXTRA_CONVERSATION_ID = "conversation_id" }
}
