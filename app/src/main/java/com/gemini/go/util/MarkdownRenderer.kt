package com.gemini.go.util

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.widget.TextView

object MarkdownRenderer {
    private val HEADER_REGEX = Regex("^(#{1,6})\\s+(.+)$")
    private val BULLET_REGEX = Regex("^(\\s*)[*\\-+]\\s+(.+)$")
    private val NUMBERED_REGEX = Regex("^(\\s*)(\\d+)\\.\\s+(.+)$")
    private val CHECKLIST_REGEX = Regex("^(\\s*)[*\\-+]\\s+\\[([ xX])]\\s+(.+)$")
    private val HR_REGEX = Regex("^([-*_])\\1{2,}\\s*$")
    private val BOLD_REGEX = Regex("\\*\\*(.+?)\\*\\*")
    private val BOLD_UNDERSCORE_REGEX = Regex("__(.+?)__")
    private val ITALIC_REGEX = Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)")
    private val ITALIC_UNDERSCORE_REGEX = Regex("_(.+?)_")
    private val BOLD_ITALIC_REGEX = Regex("\\*\\*\\*(.+?)\\*\\*\\*")
    private val CODE_REGEX = Regex("`([^`]+)`")
    private val STRIKE_REGEX = Regex("~~(.+?)~~")
    private val LINK_REGEX = Regex("\\[([^]]+)]\\(([^)]+)\\)")
    private val AUTOLINK_REGEX = Regex("<(https?://[^>]+)>")
    private val SUBSCRIPT_REGEX = Regex("~([^~\\s]+)~")
    private val SUPERSCRIPT_REGEX = Regex("\\^([^\\^\\s]+)\\^")

    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.contains("-")) return false
        if (!trimmed.contains("|")) return false
        return trimmed.all { it.isWhitespace() || it == '|' || it == '-' || it == ':' }
    }

    fun render(markdown: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder()
        // Asegurar trailing newline para detectar tablas en la última línea
        val lines = (if (markdown.endsWith("\n")) markdown else markdown + "\n").lines()
        var inCodeBlock = false
        val codeBlockBuffer = StringBuilder()
        var inTable = false
        val tableRows = mutableListOf<List<String>>()
        var i = 0
        while (i < lines.size) {
            val rawLine = lines[i]
            // Code fence
            if (rawLine.trim().startsWith("```")) {
                if (inCodeBlock) {
                    flushCodeBlock(sb, codeBlockBuffer.toString(), i < lines.lastIndex)
                    codeBlockBuffer.clear()
                    inCodeBlock = false
                } else {
                    if (inTable) { flushTable(sb, tableRows); tableRows.clear(); inTable = false }
                    inCodeBlock = true
                }
                i++; continue
            }
            if (inCodeBlock) { codeBlockBuffer.append(rawLine).append("\n"); i++; continue }

            // Table detection
            if (!inTable && rawLine.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                inTable = true
                tableRows.add(parseTableRow(rawLine))
                i++  // skip header
                i++  // skip separator
                continue
            }
            if (inTable) {
                if (rawLine.contains("|") && rawLine.isNotBlank()) {
                    tableRows.add(parseTableRow(rawLine))
                    i++
                    continue
                } else {
                    flushTable(sb, tableRows)
                    tableRows.clear()
                    inTable = false
                    // Fall through
                }
            }

            val headerMatch = HEADER_REGEX.find(rawLine)
            val checklistMatch = CHECKLIST_REGEX.find(rawLine)
            val bulletMatch = BULLET_REGEX.find(rawLine)
            val numberedMatch = NUMBERED_REGEX.find(rawLine)
            val hrMatch = HR_REGEX.find(rawLine)
            when {
                headerMatch != null -> {
                    val level = headerMatch.groupValues[1].length
                    val text = headerMatch.groupValues[2]
                    val start = sb.length
                    sb.append(renderInline(text))
                    val sizeMultiplier = when (level) { 1 -> 1.6f; 2 -> 1.4f; 3 -> 1.25f; 4 -> 1.15f; 5 -> 1.05f; else -> 1.0f }
                    sb.setSpan(RelativeSizeSpan(sizeMultiplier), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (i < lines.lastIndex) sb.append("\n")
                }
                checklistMatch != null -> {
                    val indent = checklistMatch.groupValues[1].length
                    val checked = checklistMatch.groupValues[2].lowercase() == "x"
                    val text = checklistMatch.groupValues[3]
                    val start = sb.length
                    sb.append(if (checked) "☑  " else "☐  ")
                    sb.append(renderInline(text))
                    if (checked) sb.setSpan(StrikethroughSpan(), start + 3, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(LeadingMarginSpan.Standard(indent * 2 + 24, indent * 2 + 24), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (i < lines.lastIndex) sb.append("\n")
                }
                bulletMatch != null -> {
                    val indent = bulletMatch.groupValues[1].length
                    val text = bulletMatch.groupValues[2]
                    val start = sb.length
                    sb.append("•  ")
                    sb.append(renderInline(text))
                    sb.setSpan(BulletSpan(24 + indent * 16), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (i < lines.lastIndex) sb.append("\n")
                }
                numberedMatch != null -> {
                    val indent = numberedMatch.groupValues[1].length
                    val num = numberedMatch.groupValues[2]
                    val text = numberedMatch.groupValues[3]
                    val start = sb.length
                    sb.append("$num.  ")
                    sb.append(renderInline(text))
                    sb.setSpan(LeadingMarginSpan.Standard(indent * 16 + 24, indent * 16 + 24), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (i < lines.lastIndex) sb.append("\n")
                }
                hrMatch != null -> {
                    val start = sb.length
                    sb.append("────────────────────────")
                    sb.setSpan(StrikethroughSpan(), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(RelativeSizeSpan(0.7f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (i < lines.lastIndex) sb.append("\n")
                }
                rawLine.startsWith(">") -> {
                    val text = rawLine.removePrefix(">").trim()
                    val start = sb.length
                    sb.append("▎ ")
                    sb.append(renderInline(text))
                    sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(BackgroundColorSpan(0x22FFFFFF), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    if (i < lines.lastIndex) sb.append("\n")
                }
                else -> {
                    sb.append(renderInline(rawLine))
                    if (i < lines.lastIndex) sb.append("\n")
                }
            }
            i++
        }
        if (inCodeBlock && codeBlockBuffer.isNotEmpty()) flushCodeBlock(sb, codeBlockBuffer.toString(), false)
        if (inTable) flushTable(sb, tableRows)
        return sb
    }

    private fun flushCodeBlock(sb: SpannableStringBuilder, code: String, addNewline: Boolean) {
        val text = code.trimEnd()
        val start = sb.length
        sb.append(text)
        sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(BackgroundColorSpan(0x33FFFFFF), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(RelativeSizeSpan(0.9f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (addNewline) sb.append("\n")
    }

    private fun parseTableRow(line: String): List<String> {
        val trimmed = line.trim().trim('|')
        return trimmed.split("|").map { it.trim() }
    }

    /**
     * Renders a table using ASCII characters (|, -, +) which are guaranteed to be
     * monospace on ALL Android devices.
     *
     * Key fixes vs previous version:
     *  - No 24-char truncation (was cutting content)
     *  - Uses display width (counts chars properly, handles Unicode)
     *  - Wraps long cells instead of truncating
     *  - Header row is bold
     *  - Cells render inline markdown (**bold**, *italic*, `code`)
     */
    private fun flushTable(sb: SpannableStringBuilder, rows: List<List<String>>) {
        if (rows.isEmpty()) return
        val dataRows = rows
        val start = sb.length
        val numCols = dataRows.maxOf { it.size }

        // Compute column widths based on actual content (no truncation)
        // Cap at 40 chars max to avoid super wide tables on mobile
        val maxColWidth = 40
        val colWidths = IntArray(numCols) { 0 }
        for (row in dataRows) {
            for (j in row.indices) {
                val cellLen = row[j].length
                if (cellLen > colWidths[j]) colWidths[j] = minOf(cellLen, maxColWidth)
            }
        }

        // Helper to build a border line: +---+---+---+
        fun appendBorder() {
            for (j in 0 until numCols) sb.append("+").append("-".repeat(colWidths[j] + 2))
            sb.append("+\n")
        }

        // Top border
        appendBorder()

        // Header row
        val header = dataRows.first()
        for (j in 0 until numCols) {
            sb.append("| ")
            val cellText = header.getOrElse(j) { "" }
            val cellStart = sb.length
            sb.append(renderInline(cellText))
            // Pad to colWidth
            val padding = colWidths[j] - cellText.length
            if (padding > 0) sb.append(" ".repeat(padding))
            sb.setSpan(StyleSpan(Typeface.BOLD), cellStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(" ")
        }
        sb.append("|\n")

        // Header-body separator
        appendBorder()

        // Body rows
        for (r in 1 until dataRows.size) {
            val row = dataRows[r]
            for (j in 0 until numCols) {
                sb.append("| ")
                val cellText = row.getOrElse(j) { "" }
                // Truncate if exceeds max width (with ellipsis)
                val displayText = if (cellText.length > maxColWidth) {
                    cellText.take(maxColWidth - 1) + "…"
                } else {
                    cellText
                }
                sb.append(renderInline(displayText))
                val padding = colWidths[j] - displayText.length
                if (padding > 0) sb.append(" ".repeat(padding))
                sb.append(" ")
            }
            sb.append("|")
            if (r < dataRows.size - 1) sb.append("\n")
        }
        sb.append("\n")

        // Bottom border
        appendBorder()

        // Apply monospace style to the whole table
        sb.setSpan(TypefaceSpan("monospace"), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(RelativeSizeSpan(0.85f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(BackgroundColorSpan(0x22FFFFFF), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun renderInline(text: String): SpannableStringBuilder {
        val sb = SpannableStringBuilder(text)
        applyInline(sb, BOLD_ITALIC_REGEX, 6) { s, e -> sb.setSpan(StyleSpan(Typeface.BOLD_ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyInline(sb, BOLD_REGEX, 4) { s, e -> sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyInline(sb, BOLD_UNDERSCORE_REGEX, 4) { s, e -> sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyInline(sb, ITALIC_REGEX, 2) { s, e -> sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyInline(sb, ITALIC_UNDERSCORE_REGEX, 2) { s, e -> sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyInline(sb, CODE_REGEX, 2) { s, e ->
            sb.setSpan(BackgroundColorSpan(0x44FFFFFF), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(TypefaceSpan("monospace"), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.9f), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        applyInline(sb, STRIKE_REGEX, 4) { s, e -> sb.setSpan(StrikethroughSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyInline(sb, SUBSCRIPT_REGEX, 2) { s, e -> sb.setSpan(SubscriptSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); sb.setSpan(RelativeSizeSpan(0.7f), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyInline(sb, SUPERSCRIPT_REGEX, 2) { s, e -> sb.setSpan(SuperscriptSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); sb.setSpan(RelativeSizeSpan(0.7f), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
        applyLinks(sb)
        return sb
    }

    private fun applyInline(sb: SpannableStringBuilder, regex: Regex, markerTotalLen: Int, applySpan: (start: Int, end: Int) -> Unit) {
        val markerLen = markerTotalLen / 2
        val matches = regex.findAll(sb.toString()).toList()
        for (match in matches.reversed()) {
            val start = match.range.first + markerLen
            val end = match.range.last + 1 - markerLen
            if (start < end) applySpan(start, end)
            sb.delete(match.range.last + 1 - markerLen, match.range.last + 1)
            sb.delete(match.range.first, match.range.first + markerLen)
        }
    }

    private fun applyLinks(sb: SpannableStringBuilder) {
        val matches = LINK_REGEX.findAll(sb.toString()).toList()
        for (match in matches.reversed()) {
            val linkText = match.groupValues[1]; val url = match.groupValues[2]
            val matchStart = match.range.first; val matchEnd = match.range.last + 1
            sb.replace(matchStart, matchEnd, linkText)
            val newEnd = matchStart + linkText.length
            sb.setSpan(URLSpan(url), matchStart, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(UnderlineSpan(), matchStart, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val autoMatches = AUTOLINK_REGEX.findAll(sb.toString()).toList()
        for (match in autoMatches.reversed()) {
            val url = match.groupValues[1]; val matchStart = match.range.first; val matchEnd = match.range.last + 1
            sb.replace(matchStart, matchEnd, url); val newEnd = matchStart + url.length
            sb.setSpan(URLSpan(url), matchStart, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(UnderlineSpan(), matchStart, newEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    fun enableLinks(textView: TextView) { textView.movementMethod = LinkMovementMethod.getInstance() }
}
