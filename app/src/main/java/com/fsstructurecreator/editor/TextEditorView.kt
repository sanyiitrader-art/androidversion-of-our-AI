package com.fsstructurecreator.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsstructurecreator.ui.CharcoalBg
import com.fsstructurecreator.ui.Mint
import com.fsstructurecreator.ui.TextPrimary
import com.fsstructurecreator.ui.TextSecondary

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

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()

    val fontSizeSp = 13.sp
    val lineHeightSp = 20.sp
    val monoStyle = remember {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSizeSp,
            lineHeight = lineHeightSp,
            color = TextPrimary
        )
    }

    val charWidthPx = remember(monoStyle) {
        textMeasurer.measure(AnnotatedString("M"), style = monoStyle).size.width.toFloat()
    }
    val lineHeightPx = with(density) { lineHeightSp.toPx() }
    val lineHeightDp = with(density) { lineHeightPx.toDp() }

    val extension = openFile.name.substringAfterLast('.', "")

    var fieldValue by remember(openFile.uri) { mutableStateOf(TextFieldValue(openFile.content)) }
    var activeHighlightRange by remember(openFile.uri) { mutableStateOf<IntRange?>(null) }

    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    var viewportWidthPx by remember { mutableStateOf(0) }
    var viewportHeightPx by remember { mutableStateOf(0) }
    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource, openFile.uri) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) activeHighlightRange = null
        }
    }

    LaunchedEffect(highlightRequest) {
        val request = highlightRequest ?: return@LaunchedEffect
        val start = absoluteOffset(fieldValue.text, request.lineNumber, request.matchStart)
        val end = absoluteOffset(fieldValue.text, request.lineNumber, request.matchEnd)
        if (start >= end || end > fieldValue.text.length) {
            onHighlightConsumed()
            return@LaunchedEffect
        }
        activeHighlightRange = start..end

        val targetTop = (((request.lineNumber - 1) * lineHeightPx) - 80f).coerceAtLeast(0f)
        val targetLeft = ((request.matchStart * charWidthPx) - 80f).coerceAtLeast(0f)
        vScroll.animateScrollTo(targetTop.toInt().coerceIn(0, vScroll.maxValue))
        hScroll.animateScrollTo(targetLeft.toInt().coerceIn(0, hScroll.maxValue))

        onHighlightConsumed()
    }

    LaunchedEffect(fieldValue.selection, viewportWidthPx, viewportHeightPx) {
        if (viewportWidthPx <= 0 || viewportHeightPx <= 0) return@LaunchedEffect

        val cursorOffset = fieldValue.selection.end.coerceIn(0, fieldValue.text.length)
        val before = fieldValue.text.substring(0, cursorOffset)
        val cursorLine = before.count { it == '\n' }
        val cursorColumn = before.substringAfterLast('\n').length

        val cursorTop = cursorLine * lineHeightPx
        val cursorBottom = cursorTop + lineHeightPx
        val vTop = vScroll.value.toFloat()
        val vBottom = vTop + viewportHeightPx
        val vMargin = 8f
        val newV = when {
            cursorBottom + vMargin > vBottom -> cursorBottom + vMargin - viewportHeightPx
            cursorTop - vMargin < vTop -> cursorTop - vMargin
            else -> null
        }
        if (newV != null) vScroll.animateScrollTo(newV.toInt().coerceIn(0, vScroll.maxValue))

        val cursorX = cursorColumn * charWidthPx
        val hLeft = hScroll.value.toFloat()
        val hRight = hLeft + viewportWidthPx
        val hMargin = 8f
        val newH = when {
            cursorX + hMargin > hRight -> cursorX + hMargin - viewportWidthPx
            cursorX - hMargin < hLeft -> (cursorX - hMargin).coerceAtLeast(0f)
            else -> null
        }
        if (newH != null) hScroll.animateScrollTo(newH.toInt().coerceIn(0, hScroll.maxValue))
    }

    val visualTransformation = remember(extension, activeHighlightRange, fieldValue.text) {
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

    val lineTexts = fieldValue.text.split("\n")
    val cursorOffsetNow = fieldValue.selection.end.coerceIn(0, fieldValue.text.length)
    val cursorLineNow = fieldValue.text.substring(0, cursorOffsetNow).count { it == '\n' }

    Row(modifier = Modifier.fillMaxSize().background(CharcoalBg)) {
        Column(
            modifier = Modifier
                .verticalScroll(vScroll)
                .padding(top = 12.dp, start = 4.dp, end = 4.dp)
        ) {
            lineTexts.forEachIndexed { index, lineText ->
                val showNumber = lineText.isNotEmpty() || index == cursorLineNow
                if (showNumber) {
                    LineNumberSquare(number = index + 1, height = lineHeightDp)
                } else {
                    Spacer(modifier = Modifier.height(lineHeightDp))
                }
            }
            Spacer(modifier = Modifier.height(lineHeightDp))
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .onSizeChanged {
                    viewportWidthPx = it.width
                    viewportHeightPx = it.height
                }
                .verticalScroll(vScroll)
                .horizontalScroll(hScroll)
                .padding(12.dp)
        ) {
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    val textChanged = newValue.text != fieldValue.text
                    fieldValue = newValue
                    if (textChanged) onContentChange(newValue.text)
                },
                visualTransformation = visualTransformation,
                interactionSource = interactionSource,
                textStyle = monoStyle,
                cursorBrush = SolidColor(TextPrimary)
            )
        }
    }
}

@Composable
private fun LineNumberSquare(number: Int, height: Dp) {
    Box(
        modifier = Modifier
            .height(height)
            .border(1.dp, Mint, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = number.toString(),
            color = Mint,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

private fun absoluteOffset(content: String, lineNumber: Int, column: Int): Int {
    val lines = content.split("\n")
    var offset = 0
    for (i in 0 until (lineNumber - 1).coerceAtMost(lines.size)) {
        offset += lines[i].length + 1
    }
    return (offset + column).coerceAtMost(content.length)
}