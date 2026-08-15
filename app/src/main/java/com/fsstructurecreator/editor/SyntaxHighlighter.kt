package com.fsstructurecreator.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration

// Lightweight, regex-only syntax coloring -- no parser, no AST, no
// language server (spec section 31/43: this stays a text editor, not
// an IDE). Keywords are grouped by language family so adding a new
// extension is a one-line addition, not a new architecture.

private val KeywordColor = Color(0xFF7EE8C0)      // Mint
private val StringColor = Color(0xFFE0B96A)       // warm amber
private val CommentColor = Color(0xFF6B7570)      // TextTertiary
private val NumberColor = Color(0xFF6BAEE0)       // cool blue
private val FunctionColor = Color(0xFFD9A6E8)     // soft violet

private val PYTHON_KEYWORDS = setOf(
    "def", "class", "return", "if", "elif", "else", "for", "while", "in",
    "import", "from", "as", "try", "except", "finally", "with", "pass",
    "break", "continue", "lambda", "None", "True", "False", "and", "or",
    "not", "is", "yield", "global", "nonlocal", "raise", "assert", "async",
    "await", "del"
)

private val C_STYLE_KEYWORDS = setOf(
    "if", "else", "for", "while", "do", "switch", "case", "break", "continue",
    "return", "class", "struct", "enum", "interface", "extends", "implements",
    "public", "private", "protected", "static", "final", "void", "new",
    "this", "super", "try", "catch", "finally", "throw", "throws", "import",
    "package", "const", "let", "var", "function", "int", "float", "double",
    "boolean", "bool", "char", "string", "String", "null", "true", "false",
    "fun", "val", "when", "object", "companion", "override", "async", "await",
    "export", "default", "from", "as", "type", "interface", "namespace"
)

private fun keywordsFor(extension: String): Set<String> = when (extension.lowercase()) {
    "py" -> PYTHON_KEYWORDS
    "kt", "kts", "java", "c", "cpp", "h", "hpp", "cs", "js", "jsx", "ts",
    "tsx", "swift", "go", "rs" -> C_STYLE_KEYWORDS
    else -> emptySet()
}

/** Returns true only for extensions we actually attempt to highlight.
 *  Anything else (e.g. .md, .json, .xml, or no extension) renders as
 *  plain text -- per spec section 30, the editor must not become
 *  dependent on knowing every file type, so unsupported types simply
 *  get no coloring rather than a guess. */
fun isHighlightableExtension(extension: String): Boolean {
    return keywordsFor(extension).isNotEmpty()
}

fun highlightSyntax(text: String, extension: String): AnnotatedString {
    val keywords = keywordsFor(extension)
    if (keywords.isEmpty()) {
        return AnnotatedString(text)
    }

    val isPython = extension.lowercase() == "py"
    val lineCommentToken = if (isPython) "#" else "//"

    return AnnotatedString.Builder(text).apply {
        var i = 0
        while (i < text.length) {
            val remaining = text.length - i

            // Line comment: from token to end of line.
            if (text.startsWith(lineCommentToken, i)) {
                val end = text.indexOf('\n', i).let { if (it == -1) text.length else it }
                addStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic), i, end)
                i = end
                continue
            }

            // String literal: single or double quoted, no escape parsing
            // (kept simple on purpose -- this is coloring, not lexing).
            val c = text[i]
            if (c == '"' || c == '\'') {
                val end = text.indexOf(c, i + 1).let { if (it == -1) text.length else it + 1 }
                addStyle(SpanStyle(color = StringColor), i, end)
                i = end
                continue
            }

            // Number literal.
            if (c.isDigit()) {
                var end = i
                while (end < text.length && (text[end].isDigit() || text[end] == '.')) end++
                addStyle(SpanStyle(color = NumberColor), i, end)
                i = end
                continue
            }

            // Identifier: keyword, function-call, or plain.
            if (c.isLetter() || c == '_') {
                var end = i
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
                val word = text.substring(i, end)
                when {
                    word in keywords -> addStyle(SpanStyle(color = KeywordColor), i, end)
                    end < text.length && text[end] == '(' -> addStyle(SpanStyle(color = FunctionColor), i, end)
                }
                i = end
                continue
            }

            i++
        }
    }.toAnnotatedString()
}