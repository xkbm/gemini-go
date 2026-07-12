package com.gemini.go.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.gemini.go.R
import com.gemini.go.data.model.MessageEntity
import com.gemini.go.util.MarkdownBlock
import com.gemini.go.util.MarkdownBlockParser
import com.gemini.go.util.MarkdownRenderer
import com.google.android.material.button.MaterialButton

class MessagesAdapter(
    private val onCopy: (String) -> Unit = {},
    private val onRegenerate: () -> Unit = {},
    private val onShare: (String) -> Unit = {}
) : ListAdapter<MessageUiModel, RecyclerView.ViewHolder>(DIFF) {
    companion object {
        const val TYPE_USER = 1; const val TYPE_GEMINI = 2; const val TYPE_LOADING = 3
        val DIFF = object : DiffUtil.ItemCallback<MessageUiModel>() {
            override fun areItemsTheSame(a: MessageUiModel, b: MessageUiModel) = a.id == b.id
            override fun areContentsTheSame(a: MessageUiModel, b: MessageUiModel) =
                a.id == b.id && a.text == b.text && a.isStreaming == b.isStreaming && a.imagePaths == b.imagePaths && a.generatedImageBase64 == b.generatedImageBase64
        }
    }
    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return when { item.isLoading -> TYPE_LOADING; item.role == "user" -> TYPE_USER; else -> TYPE_GEMINI }
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> UserViewHolder(inflater.inflate(R.layout.item_message_user, parent, false))
            TYPE_GEMINI -> GeminiViewHolder(inflater.inflate(R.layout.item_message_gemini, parent, false))
            else -> LoadingViewHolder(inflater.inflate(R.layout.item_message_loading, parent, false))
        }
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) { is UserViewHolder -> holder.bind(item); is GeminiViewHolder -> holder.bind(item, onCopy, onRegenerate, onShare) }
    }

    class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textMessage: TextView = itemView.findViewById(R.id.textMessage)
        private val imageAttachment: ImageView = itemView.findViewById(R.id.imageAttachment)
        fun bind(item: MessageUiModel) {
            if (item.text.isBlank()) textMessage.visibility = View.GONE
            else { textMessage.visibility = View.VISIBLE; textMessage.text = item.text }
            val firstImage = item.imagePaths.split(",").firstOrNull { it.isNotBlank() }
            if (firstImage != null) { imageAttachment.visibility = View.VISIBLE; Glide.with(itemView).load(Uri.parse(firstImage)).centerCrop().into(imageAttachment) }
            else imageAttachment.visibility = View.GONE
        }
    }

    class GeminiViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageContent: LinearLayout = itemView.findViewById(R.id.messageContent)
        private val actionRow: LinearLayout = itemView.findViewById(R.id.actionRow)
        private val btnCopy: MaterialButton = itemView.findViewById(R.id.btnCopy)
        private val btnRegenerate: MaterialButton = itemView.findViewById(R.id.btnRegenerate)
        private val btnShare: MaterialButton = itemView.findViewById(R.id.btnShare)
        private val imageGenerated: ImageView = itemView.findViewById(R.id.imageGenerated)

        fun bind(item: MessageUiModel, onCopy: (String) -> Unit, onRegenerate: () -> Unit, onShare: (String) -> Unit) {
            // Clear previous content
            messageContent.removeAllViews()

            val displayText = when {
                item.text.isBlank() && item.isStreaming && item.generatedImageBase64.isBlank() -> "…"
                item.text.isBlank() && item.generatedImageBase64.isNotBlank() -> ""
                else -> item.text
            }

            if (displayText.isNotBlank()) {
                messageContent.visibility = View.VISIBLE
                // Parse into blocks and render each one — both during streaming and when done.
                // During streaming, partial tables (header without separator) fall through to
                // TextBlock naturally, so the user sees pipes as text until the table is complete,
                // then it snaps to a native TableLayout.
                val blocks = MarkdownBlockParser.parse(displayText)
                for (block in blocks) {
                    when (block) {
                        is MarkdownBlock.TextBlock -> {
                            val tv = createTextView(block.text)
                            messageContent.addView(tv)
                        }
                        is MarkdownBlock.TableBlock -> {
                            val table = createTableView(block.header, block.rows)
                            messageContent.addView(table)
                        }
                        is MarkdownBlock.CodeBlock -> {
                            val tv = createCodeView(block.code)
                            messageContent.addView(tv)
                        }
                    }
                }
                // If no blocks were created (empty text), add a placeholder
                if (messageContent.childCount == 0) {
                    val tv = createTextView(displayText)
                    messageContent.addView(tv)
                }
            } else {
                // No text — hide the content container (image-only message)
                messageContent.visibility = View.GONE
            }

            // Show generated image if present
            if (item.generatedImageBase64.isNotBlank()) {
                imageGenerated.visibility = View.VISIBLE
                try {
                    val bytes = android.util.Base64.decode(item.generatedImageBase64, android.util.Base64.DEFAULT)
                    Glide.with(itemView).load(bytes).into(imageGenerated)
                } catch (_: Exception) { imageGenerated.visibility = View.GONE }
            } else imageGenerated.visibility = View.GONE

            actionRow.visibility = if (item.isStreaming) View.GONE else View.VISIBLE
            btnCopy.setOnClickListener { onCopy(item.text) }
            btnRegenerate.setOnClickListener { onRegenerate() }
            btnShare.setOnClickListener { onShare(item.text) }
        }

        /**
         * Creates a TextView with markdown rendering (bold, italic, lists, etc.)
         */
        private fun createTextView(text: String): TextView {
            val tv = TextView(itemView.context)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            tv.layoutParams = params
            tv.text = MarkdownRenderer.render(text)
            MarkdownRenderer.enableLinks(tv)
            tv.setTextColor(Color.WHITE)
            tv.textSize = 15f
            tv.setTextIsSelectable(true)
            // Add bottom margin if there are more blocks
            params.bottomMargin = 8
            return tv
        }

        /**
         * Creates a TextView for code blocks with monospace font and dark background.
         */
        private fun createCodeView(code: String): TextView {
            val tv = TextView(itemView.context)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = 8
            tv.layoutParams = params
            tv.text = code
            tv.typeface = Typeface.MONOSPACE
            tv.textSize = 13f
            tv.setTextColor(Color.parseColor("#E0E0E0"))
            tv.setBackgroundColor(Color.parseColor("#22FFFFFF"))
            tv.setPadding(24, 16, 24, 16)
            tv.setTextIsSelectable(true)
            return tv
        }

        /**
         * Creates a native TableLayout for markdown tables.
         * This guarantees perfect alignment regardless of font.
         */
        private fun createTableView(header: List<String>, rows: List<List<String>>): TableLayout {
            val ctx = itemView.context
            val table = TableLayout(ctx)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = 8
            table.layoutParams = params
            table.isShrinkAllColumns = true
            table.isStretchAllColumns = false

            // Header row
            val headerRow = TableRow(ctx)
            headerRow.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
            headerRow.setBackgroundColor(Color.parseColor("#2D2D2D"))
            for (cellText in header) {
                val cell = createTableCell(ctx, cellText, isHeader = true)
                headerRow.addView(cell)
            }
            table.addView(headerRow)

            // Body rows
            for ((rowIdx, row) in rows.withIndex()) {
                val tableRow = TableRow(ctx)
                tableRow.layoutParams = TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT)
                // Alternating row backgrounds for readability
                if (rowIdx % 2 == 1) {
                    tableRow.setBackgroundColor(Color.parseColor("#1A1A1A"))
                }
                for (cellText in row) {
                    val cell = createTableCell(ctx, cellText, isHeader = false)
                    tableRow.addView(cell)
                }
                table.addView(tableRow)
            }

            return table
        }

        /**
         * Creates a single table cell (TextView inside a TableRow).
         */
        private fun createTableCell(ctx: Context, text: String, isHeader: Boolean): TextView {
            val cell = TextView(ctx)
            val params = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
            params.setMargins(1, 1, 1, 1)  // thin border between cells
            cell.layoutParams = params
            cell.text = MarkdownRenderer.render(text)
            MarkdownRenderer.enableLinks(cell)
            cell.setTextColor(Color.WHITE)
            cell.textSize = 14f
            cell.setPadding(20, 16, 20, 16)
            cell.setTextIsSelectable(true)
            if (isHeader) {
                cell.setTypeface(cell.typeface, Typeface.BOLD)
            }
            // Dark background for each cell creates grid lines
            cell.setBackgroundColor(Color.parseColor("#000000"))
            return cell
        }
    }

    class LoadingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)
}

data class MessageUiModel(
    val id: String, val role: String, val text: String, val imagePaths: String = "",
    val isStreaming: Boolean = false, val isLoading: Boolean = false,
    val generatedImageBase64: String = "", val generatedImageMime: String = ""
) {
    companion object {
        fun from(entity: MessageEntity): MessageUiModel = MessageUiModel(entity.id, entity.role, entity.text, entity.imagePaths, entity.isStreaming, generatedImageBase64 = entity.generatedImageBase64, generatedImageMime = entity.generatedImageMime)
        fun loading(): MessageUiModel = MessageUiModel("loading_${System.currentTimeMillis()}", "model", "", isStreaming = true, isLoading = true)
    }
}
