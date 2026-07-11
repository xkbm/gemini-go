package com.gemini.go.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gemini.go.GeminiApp
import com.gemini.go.R
import com.gemini.go.data.model.GeminiModel
import com.gemini.go.databinding.ActivitySettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val prefs get() = (application as GeminiApp).prefs
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.editApiKey.setText(prefs.apiKey)
        binding.editApiKey.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) prefs.apiKey = binding.editApiKey.text.toString().trim() }
        binding.btnOpenGetKey.setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))) }
        setupModelSelector()
        binding.sliderTemperature.value = prefs.temperature
        binding.textTemperatureValue.text = String.format("%.1f", prefs.temperature)
        binding.sliderTemperature.addOnChangeListener { _, value, _ -> binding.textTemperatureValue.text = String.format("%.1f", value); prefs.temperature = value }
        binding.editSystemPrompt.setText(prefs.systemPrompt)
        binding.editSystemPrompt.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) prefs.systemPrompt = binding.editSystemPrompt.text.toString() }
        binding.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.setting_clear_history).setMessage(R.string.setting_clear_history_summary)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete) { _, _ -> lifecycleScope.launch { (application as GeminiApp).repository.clearAllConversations(); Snackbar.make(binding.root, "Conversaciones eliminadas", Snackbar.LENGTH_SHORT).show() } }
                .show()
        }
        binding.btnAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle(R.string.setting_about).setMessage(R.string.about_text).setPositiveButton(R.string.save, null).show()
        }
    }
    private fun setupModelSelector() {
        val currentModel = GeminiModel.fromId(prefs.modelId)
        binding.textCurrentModel.text = currentModel.displayName
        binding.textCurrentModelId.text = currentModel.id
        binding.btnSelectModel.setOnClickListener { showModelDialog() }
        binding.btnCustomModel.setOnClickListener { showCustomModelDialog() }
    }
    private fun showModelDialog() {
        val presets = GeminiModel.PRESETS
        val currentId = prefs.modelId
        val current = GeminiModel.fromId(currentId)
        val items = if (current.isCustom) listOf(current) + presets else presets
        val labels = items.map { "${it.displayName}\n  ${it.description}" }.toTypedArray()
        val checked = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_model)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val chosen = items[which]; prefs.modelId = chosen.id
                binding.textCurrentModel.text = chosen.displayName; binding.textCurrentModelId.text = chosen.id; dialog.dismiss()
            }
            .setNeutralButton(R.string.action_custom_model) { _, _ -> showCustomModelDialog() }
            .setNegativeButton(R.string.cancel, null).show()
    }
    private fun showCustomModelDialog() {
        val edit = com.google.android.material.textfield.TextInputEditText(this).apply {
            hint = "gemini-3.1-flash-lite"; setText(prefs.modelId); setSingleLine(true); setPadding(48, 32, 48, 32); setTextColor(android.graphics.Color.WHITE)
        }
        val container = com.google.android.material.textfield.TextInputLayout(this).apply { setPadding(48, 32, 48, 16); hint = "ID del modelo"; addView(edit) }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.setting_custom_model).setMessage(R.string.setting_custom_model_summary).setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val newId = edit.text.toString().trim()
                if (newId.isNotEmpty()) {
                    prefs.modelId = newId; val m = GeminiModel.fromId(newId)
                    binding.textCurrentModel.text = m.displayName; binding.textCurrentModelId.text = m.id
                    Snackbar.make(binding.root, "Modelo guardado: ${m.id}", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null).show()
    }
    override fun onPause() {
        super.onPause()
        prefs.apiKey = binding.editApiKey.text.toString().trim()
        prefs.systemPrompt = binding.editSystemPrompt.text.toString()
    }
}
