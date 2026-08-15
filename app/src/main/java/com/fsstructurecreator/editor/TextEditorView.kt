package com.fsstructurecreator.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsstructurecreator.ui.CharcoalBg
import com.fsstructurecreator.ui.TextPrimary
import com.fsstructurecreator.ui.TextSecondary
import kotlinx.coroutines.launch

private val HighlightMint = Color(0x557EE8C0) // transparent mint

@Composable
fun TextEditorView(
    openFile: OpenFile?,
    onContentChange: (String) -> Unit,
    highlightRequest: TextSearchResult?,
    onHighlightConsumed: () -> Unit
) {
    if (openFile == null) {
        Box(modifier = Modifier.fillMaxSize().background(CharcoalBg)) {
            Text(
                text = "No file open",
                color = TextSecondary,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val extension = openFile.name.substringAfterLast('.', "")
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var activeHighlightRange by remember(openFile.uri) { mutableStateOf<IntRange?>(null) }

    LaunchedEffect(highlightRequest, textLayout) {
        val request = highlightRequest ?: return@LaunchedEffect
        val layout = textLayout ?: return@LaunchedEffect

        val start = absoluteOffset(openFile.content, request.lineNumber, request.matchStart)
        val end = absoluteOffset(openFile.content, request.lineNumber, request.matchEnd)
        if (start >= end || end > openFile.content.length) {
            onHighlightConsumed()
            return@LaunchedEffect
        }

        activeHighlightRange = start..end

        val lineIndex = layout.getLineForOffset(start)
        val targetTop = (layout.getLineTop(lineIndex) - 80f).toInt().coerceAtLeast(0)
        scope.launch {
            scrollState.animateScrollTo(targetTop.coerceAtMost(scrollState.maxValue))
        }

        onHighlightConsumed()
    }

    val visualTransformation = remember(extension, activeHighlightRange, openFile.content) {
        VisualTransformation { annotatedString ->
            val base = if (isHighlightableExtension(extension)) {
                highlightSyntax(annotatedString.text, extension)
            } else {
                AnnotatedString(annotatedString.text)
            }

            val range = activeHighlightRange
            val result = if (range != null && range.last <= base.length) {
                AnnotatedString.Builder(base).apply {
                    addStyle(SpanStyle(background = HighlightMint), range.first, range.last)
                }.toAnnotatedString()
            } else base

            TransformedText(result, OffsetMapping.Identity)
        }
    }

    BasicTextField(
        value = openFile.content,
        onValueChange = onContentChange,
        onTextLayout = { textLayout = it },
        visualTransformation = visualTransformation,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = TextPrimary
        ),
        cursorBrush = SolidColor(TextPrimary),
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .padding(12.dp)
            .verticalScroll(scrollState)
    )
}

private fun absoluteOffset(content: String, lineNumber: Int, column: Int): Int {
    val lines = content.split("\n")
    var offset = 0
    for (i in 0 until (lineNumber - 1).coerceAtMost(lines.size)) {
        offset += lines[i].length + 1
    }
    return (offset + column).coerceAtMost(content.length)
}