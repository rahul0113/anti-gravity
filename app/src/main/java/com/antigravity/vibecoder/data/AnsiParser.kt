package com.antigravity.vibecoder.data

import androidx.compose.ui.graphics.Color

data class AnsiSpan(
    val text: String,
    val color: Color = Color.Unspecified,
    val background: Color = Color.Unspecified,
    val bold: Boolean = false
)

object AnsiParser {
    private val ANSI_REGEX = Regex("\u001B\\[([0-9;]*)m")

    // Standard 8-color terminal palette
    private val COLORS = arrayOf(
        Color(0xFF1E1E1E), // 0 Black
        Color(0xFFCD3131), // 1 Red
        Color(0xFF0FBC5F), // 2 Green
        Color(0xFFF4D847), // 3 Yellow
        Color(0xFF009CD9), // 4 Blue
        Color(0xFFD44ADA), // 5 Magenta
        Color(0xFF00BCBC), // 6 Cyan
        Color(0xFFE5E5E5)  // 7 White
    )

    // Bright colors (90-97)
    private val BRIGHT_COLORS = arrayOf(
        Color(0xFF666666), // 8 Bright Black (Gray)
        Color(0xFFFF6B6B), // 9 Bright Red
        Color(0xFF69DB7C), // 10 Bright Green
        Color(0xFFFFF06B), // 11 Bright Yellow
        Color(0xFF74C0FC), // 12 Bright Blue
        Color(0xFFD4A5FF), // 13 Bright Magenta
        Color(0xFF86E3CE), // 14 Bright Cyan
        Color(0xFFF5F5F5)  // 15 Bright White
    )

    fun parse(text: String): List<AnsiSpan> {
        val spans = mutableListOf<AnsiSpan>()
        var currentColor = Color.Unspecified
        var currentBg = Color.Unspecified
        var currentBold = false
        var lastIndex = 0

        ANSI_REGEX.findAll(text).forEach { match ->
            // Add text before this escape sequence
            val before = text.substring(match.range.first.coerceAtLeast(lastIndex), match.range.first)
            if (before.isNotEmpty()) {
                spans.add(AnsiSpan(before, currentColor, currentBg, currentBold))
            }

            // Parse escape codes
            val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
            var i = 0
            while (i < codes.size) {
                when (codes[i]) {
                    0 -> { // Reset
                        currentColor = Color.Unspecified
                        currentBg = Color.Unspecified
                        currentBold = false
                    }
                    1 -> currentBold = true
                    22 -> currentBold = false
                    30, 31, 32, 33, 34, 35, 36, 37 -> {
                        currentColor = COLORS[codes[i] - 30]
                    }
                    39 -> currentColor = Color.Unspecified
                    40, 41, 42, 43, 44, 45, 46, 47 -> {
                        currentBg = COLORS[codes[i] - 40]
                    }
                    49 -> currentBg = Color.Unspecified
                    90, 91, 92, 93, 94, 95, 96, 97 -> {
                        currentColor = BRIGHT_COLORS[codes[i] - 90]
                    }
                }
                i++
            }

            lastIndex = match.range.last + 1
        }

        // Add remaining text
        if (lastIndex < text.length) {
            spans.add(AnsiSpan(text.substring(lastIndex), currentColor, currentBg, currentBold))
        }

        // If no ANSI codes found, return the whole text as one span
        if (spans.isEmpty() && text.isNotEmpty()) {
            spans.add(AnsiSpan(text))
        }

        return spans
    }

    // Strip ANSI codes from text (for plain text output)
    fun stripAnsi(text: String): String {
        return ANSI_REGEX.replace(text, "")
    }
}
