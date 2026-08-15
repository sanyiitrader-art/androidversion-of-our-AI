package com.fsstructurecreator.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle

private val KeywordColor = Color(0xFF7EE8C0)
private val StringColor = Color(0xFFE0B96A)
private val CommentColor = Color(0xFF6B7570)
private val NumberColor = Color(0xFF6BAEE0)
private val FunctionColor = Color(0xFFD9A6E8)

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
    "export", "default", "from", "as", "type", "interface", "namespace",
    "abstract", "instanceof", "trait", "def", "print"
)

private val RUBY_KEYWORDS = setOf(
    "def", "end", "class", "module", "if", "elsif", "else", "unless",
    "while", "until", "for", "in", "do", "begin", "rescue", "ensure",
    "raise", "return", "yield", "self", "nil", "true", "false", "and",
    "or", "not", "then", "case", "when", "require", "require_relative",
    "attr_accessor", "attr_reader", "attr_writer", "puts", "print"
)

private val PHP_KEYWORDS = setOf(
    "function", "class", "public", "private", "protected", "static",
    "if", "else", "elseif", "foreach", "for", "while", "do", "switch",
    "case", "break", "continue", "return", "echo", "print", "require",
    "require_once", "include", "include_once", "namespace", "use",
    "new", "extends", "implements", "interface", "abstract", "final",
    "try", "catch", "finally", "throw", "null", "true", "false", "array",
    "as", "global", "const", "isset", "unset"
)

private val SHELL_KEYWORDS = setOf(
    "if", "then", "else", "elif", "fi", "for", "while", "until", "do",
    "done", "case", "esac", "function", "return", "exit", "echo", "export",
    "local", "readonly", "shift", "break", "continue", "in", "select",
    "source", "alias", "unset", "true", "false"
)

private val SQL_KEYWORDS = setOf(
    "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET",
    "DELETE", "CREATE", "TABLE", "ALTER", "DROP", "JOIN", "INNER", "LEFT",
    "RIGHT", "OUTER", "ON", "GROUP", "BY", "ORDER", "HAVING", "LIMIT",
    "AND", "OR", "NOT", "NULL", "IS", "IN", "AS", "DISTINCT", "UNION",
    "PRIMARY", "KEY", "FOREIGN", "REFERENCES", "DEFAULT", "INDEX", "VIEW"
)

private fun keywordsFor(extension: String): Set<String> = when (extension.lowercase()) {
    "py" -> PYTHON_KEYWORDS
    "kt", "kts", "java", "c", "cpp", "h", "hpp", "cs", "js", "jsx", "ts",
    "tsx", "swift", "go", "rs", "dart", "scala", "groovy" -> C_STYLE_KEYWORDS
    "rb" -> RUBY_KEYWORDS
    "php" -> PHP_KEYWORDS
    "sh", "bash", "zsh" -> SHELL_KEYWORDS
    "sql" -> SQL_KEYWORDS
    else -> emptySet()
}

/** SQL keywords are conventionally matched case-insensitively (SELECT
 *  and select are equally valid); every other family here is
 *  case-sensitive, matching real language semantics. */
private fun isSqlLike(extension: String) = extension.lowercase() == "sql"

fun isHighlightableExtension(extension: String): Boolean {
    return keywordsFor(extension).isNotEmpty()
}

fun highlightSyntax(text: String, extension: String): AnnotatedString {
    val keywords = keywordsFor(extension)
    if (keywords.isEmpty()) {
        return AnnotatedString(text)
    }

    val ext = extension.lowercase()
    val isPython = ext == "py"
    val isRuby = ext == "rb"
    val isShell = ext in setOf("sh", "bash", "zsh")
    val hashCommentLanguage = isPython || isRuby || isShell
    val lineCommentToken = if (hashCommentLanguage) "#" else "//"
    val caseInsensitiveKeywords = isSqlLike(ext)

    return AnnotatedString.Builder(text).apply {
        var i = 0
        while (i < text.length) {
            if (text.startsWith(lineCommentToken, i)) {
                val end = text.indexOf('\n', i).let { if (it == -1) text.length else it }
                addStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic), i, end)
                i = end
                continue
            }

            val c = text[i]
            if (c == '"' || c == '\'') {
                val end = text.indexOf(c, i + 1).let { if (it == -1) text.length else it + 1 }
                addStyle(SpanStyle(color = StringColor), i, end)
                i = end
                continue
            }

            if (c.isDigit()) {
                var end = i
                while (end < text.length && (text[end].isDigit() || text[end] == '.')) end++
                addStyle(SpanStyle(color = NumberColor), i, end)
                i = end
                continue
            }

            if (c.isLetter() || c == '_') {
                var end = i
                while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) end++
                val word = text.substring(i, end)
                val isKeyword = if (caseInsensitiveKeywords) {
                    keywords.any { it.equals(word, ignoreCase = true) }
                } else {
                    word in keywords
                }
                when {
                    isKeyword -> addStyle(SpanStyle(color = KeywordColor), i, end)
                    end < text.length && text[end] == '(' -> addStyle(SpanStyle(color = FunctionColor), i, end)
                }
                i = end
                continue
            }

            i++
        }
    }.toAnnotatedString()
}