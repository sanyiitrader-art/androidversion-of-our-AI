package com.fsstructurecreator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private const val URL_TAG = "URL"

private data class InlineBuildResult(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>
)

// InlineTextContent and appendInlineContent both live in
// androidx.compose.foundation.text -- NOT androidx.compose.ui.text.
// The previous version imported them from the wrong package, which
// left them (and appendInlineContent) fully unresolved, and the
// resulting broken type inference cascaded into the withStyle/append
// calls further down reporting as unresolved too, even though those
// really are plain AnnotatedString.Builder members.
private fun buildInlineContent(text: String): InlineBuildResult {
    val builder = AnnotatedString.Builder()
    val inlineMap = mutableMapOf<String, InlineTextContent>()
    var codeCounter = 0
    var i = 0

    while (i < text.length) {
        val c = text[i]

        if (c == '`') {
            val close = text.indexOf('`', i + 1)
            if (close > i) {
                val code = text.substring(i + 1, close)
                val id = "code_${codeCounter++}"
                val widthEm = (code.length * 0.62f + 1.1f)
                builder.appendInlineContent(id, code)
                inlineMap[id] = InlineTextContent(
                    Placeholder(
                        width = widthEm.em,
                        height = 1.35.em,
                        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color(0xFF1C1F1E), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF2B2F2D), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = code,
                            color = Color(0xFF7EE8C0),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                }
                i = close + 1
                continue
            }
        }

        if (text.startsWith("***", i)) {
            val close = text.indexOf("***", i + 3)
            if (close > i) {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 3, close))
                }
                i = close + 3
                continue
            }
        }

        if (text.startsWith("**", i)) {
            val close = text.indexOf("**", i + 2)
            if (close > i) {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, close))
                }
                i = close + 2
                continue
            }
        }

        if (text.startsWith("~~", i)) {
            val close = text.indexOf("~~", i + 2)
            if (close > i) {
                builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(text.substring(i + 2, close))
                }
                i = close + 2
                continue
            }
        }

        if (c == '*' && (i + 1 >= text.length || text[i + 1] != '*')) {
            val close = text.indexOf('*', i + 1)
            if (close > i) {
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 1, close))
                }
                i = close + 1
                continue
            }
        }

        if (c == '[') {
            val labelEnd = text.indexOf(']', i + 1)
            if (labelEnd > i && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                val urlEnd = text.indexOf(')', labelEnd + 2)
                if (urlEnd > labelEnd) {
                    val label = text.substring(i + 1, labelEnd)
                    val url = text.substring(labelEnd + 2, urlEnd)
                    val start = builder.length
                    builder.withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                        append(label)
                    }
                    builder.addStringAnnotation(URL_TAG, url, start, builder.length)
                    i = urlEnd + 1
                    continue
                }
            }
        }

        builder.append(c)
        i++
    }

    return InlineBuildResult(builder.toAnnotatedString(), inlineMap)
}

@Composable
fun InlineMarkdownText(
    text: String,
    color: Color,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit = 14.sp
) {
    val result = remember(text) { buildInlineContent(text) }
    val uriHandler = LocalUriHandler.current
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = result.text,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize,
        inlineContent = result.inlineContent,
        onTextLayout = { layoutResult = it },
        modifier = Modifier.pointerInput(text) {
            detectTapGestures { pos ->
                val layout = layoutResult ?: return@detectTapGestures
                val offset = layout.getOffsetForPosition(pos)
                result.text.getStringAnnotations(URL_TAG, offset, offset).firstOrNull()?.let {
                    runCatching { uriHandler.openUri(it.item) }
                }
            }
        }
    )
}