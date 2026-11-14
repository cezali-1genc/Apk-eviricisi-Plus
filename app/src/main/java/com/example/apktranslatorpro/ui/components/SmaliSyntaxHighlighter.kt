package com.example.apktranslatorpro.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

data class SmaliColorScheme(
    val directiveColor: Color,
    val instructionColor: Color,
    val registerColor: Color,
    val labelColor: Color,
    val stringLiteralColor: Color,
    val typeDescriptorColor: Color,
    val commentColor: Color,
    val numberColor: Color,
    val defaultTextColor: Color
)

object SmaliSyntaxHighlighter {

    val DarkSmaliScheme = SmaliColorScheme(
        directiveColor = Color(0xFF38BDF8),     // Cyan for .class, .method, .locals
        instructionColor = Color(0xFFC084FC),   // Violet for invoke-virtual, const-string, return
        registerColor = Color(0xFFFBBF24),      // Amber for v0, p0, p1
        labelColor = Color(0xFF34D399),         // Emerald for :cond_0, :goto_1
        stringLiteralColor = Color(0xFFFB7185), // Rose for "..."
        typeDescriptorColor = Color(0xFF93C5FD),// Soft blue for Ljava/lang/String;
        commentColor = Color(0xFF64748B),       // Muted slate for # comments
        numberColor = Color(0xFFFDBA74),        // Orange for hex/numbers
        defaultTextColor = Color(0xFFE2E8F0)
    )

    val LightSmaliScheme = SmaliColorScheme(
        directiveColor = Color(0xFF0369A1),
        instructionColor = Color(0xFF7E22CE),
        registerColor = Color(0xFFB45309),
        labelColor = Color(0xFF047857),
        stringLiteralColor = Color(0xFFBE123C),
        typeDescriptorColor = Color(0xFF1D4ED8),
        commentColor = Color(0xFF64748B),
        numberColor = Color(0xFFC2410C),
        defaultTextColor = Color(0xFF0F172A)
    )

    private val directiveRegex = Regex("""\.[a-zA-Z_-]+""")
    private val labelRegex = Regex("""(?<=^|\s):[A-Za-z0-9_]+""")
    private val registerRegex = Regex("""\b[vp]\d+\b""")
    private val instructionRegex = Regex(
        """\b(?:nop|move(?:-[a-z0-9]+)*|return(?:-[a-z0-9]+)*|const(?:-[a-z0-9/]+)*|""" +
            """monitor-(?:enter|exit)|check-cast|instance-of|array-length|new-instance|new-array|""" +
            """filled-new-array(?:/range)?|fill-array-data|throw|goto(?:/16|/32)?|""" +
            """packed-switch|sparse-switch|cmpl-[a-z]+|cmpg-[a-z]+|cmp-long|""" +
            """if-(?:eq|ne|lt|ge|gt|le)(?:z)?|aget(?:-[a-z]+)?|aput(?:-[a-z]+)?|""" +
            """iget(?:-[a-z]+)?|iput(?:-[a-z]+)?|sget(?:-[a-z]+)?|sput(?:-[a-z]+)?|""" +
            """invoke-(?:virtual|super|direct|static|interface|polymorphic|custom)(?:/range)?|""" +
            """neg-[a-z]+|not-[a-z]+|[a-z]+-to-[a-z]+|(?:add|sub|mul|div|rem|and|or|xor|shl|shr|ushr)-[a-z0-9/]+)\b"""
    )
    private val stringRegex = Regex(""""(?:[^"\\]|\\.)*"""")
    private val typeRegex = Regex("""L[A-Za-z0-9_/$]+;""")
    private val hexOrNumberRegex = Regex("""\b-?0x[0-9a-fA-F]+\b|\b\d+\b""")

    fun highlight(source: String, scheme: SmaliColorScheme): AnnotatedString {
        return buildAnnotatedString {
            append(source)
            addStyle(SpanStyle(color = scheme.defaultTextColor), 0, source.length)

            var lineStart = 0
            source.split('\n').forEach { line ->
                val commentIdx = findCommentIndexOutsideStrings(line)
                val codePart = if (commentIdx >= 0) line.substring(0, commentIdx) else line

                directiveRegex.findAll(codePart).forEach { m ->
                    addStyle(
                        SpanStyle(color = scheme.directiveColor, fontWeight = FontWeight.Bold),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                instructionRegex.findAll(codePart).forEach { m ->
                    addStyle(
                        SpanStyle(color = scheme.instructionColor, fontWeight = FontWeight.Medium),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                registerRegex.findAll(codePart).forEach { m ->
                    addStyle(
                        SpanStyle(color = scheme.registerColor, fontWeight = FontWeight.SemiBold),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                labelRegex.findAll(codePart).forEach { m ->
                    addStyle(
                        SpanStyle(color = scheme.labelColor, fontWeight = FontWeight.Bold),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                typeRegex.findAll(codePart).forEach { m ->
                    addStyle(
                        SpanStyle(color = scheme.typeDescriptorColor),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                hexOrNumberRegex.findAll(codePart).forEach { m ->
                    addStyle(
                        SpanStyle(color = scheme.numberColor),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                stringRegex.findAll(codePart).forEach { m ->
                    addStyle(
                        SpanStyle(color = scheme.stringLiteralColor),
                        lineStart + m.range.first,
                        lineStart + m.range.last + 1
                    )
                }

                if (commentIdx >= 0) {
                    addStyle(
                        SpanStyle(color = scheme.commentColor, fontStyle = FontStyle.Italic),
                        lineStart + commentIdx,
                        lineStart + line.length
                    )
                }

                lineStart += line.length + 1
            }
        }
    }

    private fun findCommentIndexOutsideStrings(line: String): Int {
        var inString = false
        var escaped = false
        for (i in line.indices) {
            val c = line[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (c == '\\') {
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (c == '#' && !inString) {
                return i
            }
        }
        return -1
    }
}
