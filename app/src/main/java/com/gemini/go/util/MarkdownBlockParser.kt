package com.gemini.go.util

/**
 * Parses markdown text into structured blocks.
 * Each block is either text, a table, or a code block.
 * This allows rendering tables as native TableLayout (perfect alignment)
 * instead of ASCII art in a TextView.
 */
sealed class MarkdownBlock {
    data class TextBlock(val text: String) : MarkdownBlock()
    data class TableBlock(val header: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class CodeBlock(val code: String) : MarkdownBlock()
}

object MarkdownBlockParser {

    fun parse(markdown: String): List<MarkdownBlock> {
        val blocks = mutableListOf<MarkdownBlock>()
        val lines = markdown.lines()
        val textBuffer = StringBuilder()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            // Code block
            if (line.trim().startsWith("```")) {
                flushText(blocks, textBuffer)
                i++ // skip opening fence
                val codeBuffer = StringBuilder()
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeBuffer.append(lines[i]).append("\n")
                    i++
                }
                blocks.add(MarkdownBlock.CodeBlock(codeBuffer.toString().trimEnd()))
                i++ // skip closing fence
                continue
            }

            // Table: line with | AND next line is separator
            if (line.contains("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                flushText(blocks, textBuffer)
                val header = parseTableRow(line)
                i += 2 // skip header and separator
                val rows = mutableListOf<List<String>>()
                while (i < lines.size && lines[i].contains("|") && lines[i].isNotBlank()) {
                    rows.add(parseTableRow(lines[i]))
                    i++
                }
                blocks.add(MarkdownBlock.TableBlock(header, rows))
                continue
            }

            // Regular text line
            textBuffer.append(line).append("\n")
            i++
        }

        flushText(blocks, textBuffer)
        return blocks
    }

    private fun flushText(blocks: MutableList<MarkdownBlock>, buffer: StringBuilder) {
        if (buffer.isNotEmpty()) {
            val text = buffer.toString().trimEnd()
            if (text.isNotEmpty()) {
                blocks.add(MarkdownBlock.TextBlock(text))
            }
            buffer.clear()
        }
    }

    private fun isTableSeparator(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (!trimmed.contains("-")) return false
        if (!trimmed.contains("|")) return false
        return trimmed.all { it.isWhitespace() || it == '|' || it == '-' || it == ':' }
    }

    private fun parseTableRow(line: String): List<String> {
        val trimmed = line.trim().trim('|')
        return trimmed.split("|").map { it.trim() }
    }
}
