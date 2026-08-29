package com.fsstructurecreator.ui

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsstructurecreator.data.Attachment
import com.fsstructurecreator.data.ChatMessage
import com.fsstructurecreator.data.MessageRole
import com.fsstructurecreator.editor.highlightSyntax
import com.fsstructurecreator.editor.isHighlightableExtension

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isLatestUserMessage: Boolean = false,
    isLatestAiMessage: Boolean = false,
    onLike: () -> Unit = {},
    onDislike: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSaveEdit: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current

    if (message.role == MessageRole.USER) {
        UserMessage(message = message, isLatest = isLatestUserMessage, clipboard = clipboard, onSaveEdit = onSaveEdit)
    } else {
        AiMessage(message = message, isLatest = isLatestAiMessage, clipboard = clipboard, onLike = onLike, onDislike = onDislike, onRetry = onRetry)
    }
}

@Composable
private fun UserMessage(message: ChatMessage, isLatest: Boolean, clipboard: ClipboardManager, onSaveEdit: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(message.id) { mutableStateOf(message.content) }
    var showAttachmentsDialog by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        if (message.attachments.isNotEmpty()) {
            AttachmentIndicator(count = message.attachments.size, onClick = { showAttachmentsDialog = true })
        }

        if (isEditing) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 280.dp)) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MintSoft, unfocusedContainerColor = MintSoft,
                        focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { editText = message.content; isEditing = false }) { Text("Discard", color = TextSecondary) }
                    TextButton(onClick = { isEditing = false; onSaveEdit(editText.trim()) }) { Text("Save", color = Mint) }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                Box {
                    Surface(
                        color = MintSoft,
                        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 4.dp),
                        modifier = Modifier.widthIn(max = 280.dp)
                            .pointerInput(message.id) {
                                detectTapGestures(onLongPress = { pressOffset ->
                                    menuOffset = DpOffset(x = pressOffset.x.toDp(), y = pressOffset.y.toDp() - 110.dp)
                                    showMenu = true
                                })
                            }
                    ) {
                        Text(
                            text = message.content, color = TextPrimary, style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Clip, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }

                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, offset = menuOffset) {
                        DropdownMenuItem(
                            text = { Text("Copy", color = TextPrimary) },
                            onClick = { showMenu = false; clipboard.setText(AnnotatedString(message.content)) }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit", color = if (isLatest) TextPrimary else TextTertiary) },
                            enabled = isLatest,
                            onClick = { showMenu = false; editText = message.content; isEditing = true }
                        )
                    }
                }
            }
        }
    }

    if (showAttachmentsDialog) {
        AttachmentListDialog(attachments = message.attachments, onDismiss = { showAttachmentsDialog = false })
    }
}

@Composable
private fun AiMessage(message: ChatMessage, isLatest: Boolean, clipboard: ClipboardManager, onLike: () -> Unit, onDislike: () -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        FormattedText(message.content)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            IconButton(onClick = { clipboard.setText(AnnotatedString(message.content)) }, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onLike, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ThumbUp, contentDescription = "Like", tint = if (message.liked) Mint else TextSecondary, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onDislike, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.ThumbDown, contentDescription = "Dislike", tint = if (message.disliked) Mint else TextSecondary, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onRetry, enabled = isLatest, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Refresh, contentDescription = "Retry", tint = if (isLatest) TextSecondary else TextTertiary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

// ---- Inline markdown (bold/italic/strike/code/link) ----

private data class InlineSegment(
    val text: String, val bold: Boolean = false, val italic: Boolean = false,
    val strike: Boolean = false, val code: Boolean = false, val href: String? = null
)

private val INLINE_PATTERN = Regex("(`[^`]+`)|(\\*\\*\\*[^*]+\\*\\*\\*)|(\\*\\*[^*]+\\*\\*)|(\\*[^*]+\\*)|(~~[^~]+~~)|(\\[[^\\]]+\\]\\([^)]+\\))")

private fun parseInlineMarkdown(text: String): List<InlineSegment> {
    val segments = mutableListOf<InlineSegment>()
    var lastIndex = 0
    for (match in INLINE_PATTERN.findAll(text)) {
        if (match.range.first > lastIndex) segments.add(InlineSegment(text.substring(lastIndex, match.range.first)))
        val token = match.value
        when {
            token.startsWith("`") -> segments.add(InlineSegment(token.trim('`'), code = true))
            token.startsWith("***") -> segments.add(InlineSegment(token.removePrefix("***").removeSuffix("***"), bold = true, italic = true))
            token.startsWith("**") -> segments.add(InlineSegment(token.removePrefix("**").removeSuffix("**"), bold = true))
            token.startsWith("~~") -> segments.add(InlineSegment(token.removePrefix("~~").removeSuffix("~~"), strike = true))
            token.startsWith("[") -> {
                val linkMatch = Regex("^\\[([^\\]]+)\\]\\(([^)]+)\\)$").find(token)
                if (linkMatch != null) segments.add(InlineSegment(linkMatch.groupValues[1], href = linkMatch.groupValues[2]))
            }
            token.startsWith("*") -> segments.add(InlineSegment(token.removePrefix("*").removeSuffix("*"), italic = true))
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) segments.add(InlineSegment(text.substring(lastIndex)))
    return segments
}

/** Inline markdown text: bold/italic/strike/links via SpanStyle, and
 *  inline code as a REAL bordered/rounded/padded pill (matching the
 *  supplied reference image exactly) using a measured InlineTextContent
 *  placeholder -- Compose's SpanStyle alone can't produce a rounded
 *  border on inline text, only a flat background rectangle. */
@Composable
private fun InlineMarkdownText(text: String, color: Color = TextPrimary) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val segments = remember(text) { parseInlineMarkdown(text) }
    val codeTextStyle = remember { TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Mint) }
    val paddingPx = with(density) { 6.dp.toPx() }
    val lineHeightPx = with(density) { 18.dp.toPx() }

    val inlineContent = remember(segments) { mutableMapOf<String, InlineTextContent>() }
    val annotated = remember(segments) {
        buildAnnotatedString {
            segments.forEachIndexed { idx, seg ->
                when {
                    seg.code -> {
                        val id = "code_$idx"
                        val measured = textMeasurer.measure(AnnotatedString(seg.text), style = codeTextStyle)
                        val widthPx = measured.size.width + paddingPx * 2
                        inlineContent[id] = InlineTextContent(
                            placeholder = Placeholder(
                                width = with(density) { widthPx.toSp() },
                                height = with(density) { lineHeightPx.toSp() },
                                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                            )
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .border(1.dp, CharcoalBorder, RoundedCornerShape(4.dp))
                                    .background(CharcoalInput, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(seg.text, style = codeTextStyle)
                            }
                        }
                        appendInlineContent(id, "\uFFFD")
                    }
                    seg.href != null -> {
                        pushStyle(SpanStyle(textDecoration = TextDecoration.Underline))
                        append(seg.text)
                        pop()
                    }
                    else -> {
                        pushStyle(SpanStyle(
                            fontWeight = if (seg.bold) FontWeight.Bold else null,
                            fontStyle = if (seg.italic) FontStyle.Italic else null,
                            textDecoration = if (seg.strike) TextDecoration.LineThrough else null
                        ))
                        append(seg.text)
                        pop()
                    }
                }
            }
        }
    }

    Text(text = annotated, inlineContent = inlineContent, color = color)
}

private fun extensionForLanguage(language: String): String = when (language.trim().lowercase()) {
    "python", "py" -> "py"; "kotlin", "kt" -> "kt"; "javascript", "js" -> "js"; "typescript", "ts" -> "ts"
    "java" -> "java"; "c" -> "c"; "cpp", "c++" -> "cpp"; "csharp", "c#", "cs" -> "cs"
    "html" -> "html"; "css" -> "css"; "json" -> "json"; "bash", "sh", "shell" -> "sh"; "sql" -> "sql"
    "ruby", "rb" -> "rb"; "php" -> "php"; "go" -> "go"; "rust", "rs" -> "rs"; "swift" -> "swift"
    "xml" -> "xml"; "yaml", "yml" -> "yml"; else -> "txt"
}

private fun downloadCodeSnippet(context: Context, language: String, code: String) {
    val ext = extensionForLanguage(language)
    val fileName = "snippet_${System.currentTimeMillis()}.$ext"
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
    resolver.openOutputStream(uri)?.use { it.write(code.toByteArray()) }
    resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
}

@Composable
private fun FormattedText(text: String) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val lines = text.split("\n")
    var i = 0

    Column(modifier = Modifier.fillMaxWidth(0.9f).padding(horizontal = 4.dp)) {
        while (i < lines.size) {
            val line = lines[i]

            if (line.trim().startsWith("```")) {
                val language = line.trim().removePrefix("```").trim().ifEmpty { "text" }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) { codeLines.add(lines[i]); i++ }
                if (i < lines.size) i++
                val codeText = codeLines.joinToString("\n")
                val ext = extensionForLanguage(language)
                val highlighted = remember(codeText, ext) {
                    if (isHighlightableExtension(ext)) highlightSyntax(codeText, ext) else AnnotatedString(codeText)
                }
                val gutterText = remember(codeLines) { codeLines.indices.joinToString("\n") { (it + 1).toString() } }

                Surface(color = CharcoalInput, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp)) {
                            Text(text = language, color = TextSecondary, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { clipboard.setText(AnnotatedString(codeText)) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                            IconButton(onClick = { downloadCodeSnippet(context, language, codeText) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Filled.FileDownload, contentDescription = "Download code", tint = TextSecondary, modifier = Modifier.size(14.dp))
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = gutterText, color = TextTertiary, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp,
                                textAlign = TextAlign.End, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                            Box(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                                Text(
                                    text = highlighted, fontFamily = FontFamily.Monospace, fontSize = 12.5.sp,
                                    softWrap = false, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
                continue
            }

            val headingMatch = Regex("^(#{1,3})\\s+(.*)$").find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                InlineMarkdownText(text = headingMatch.groupValues[2], color = TextPrimary)
                i++
                continue
            }

            if (line.trim().startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trim()); i++
                }
                Row(modifier = Modifier.padding(vertical = 4.dp).height(IntrinsicSize.Min)) {
                    Box(modifier = Modifier.fillMaxHeight().width(3.dp).background(TextTertiary))
                    Spacer(modifier = Modifier.width(8.dp))
                    InlineMarkdownText(text = quoteLines.joinToString("\n"), color = TextPrimary)
                }
                continue
            }

            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                val items = mutableListOf<String>()
                while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
                    items.add(lines[i].trim().removePrefix("- ").removePrefix("* ")); i++
                }
                Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                    items.forEach { item ->
                        Row { Text("•  ", color = TextPrimary); InlineMarkdownText(text = item, color = TextPrimary) }
                    }
                }
                continue
            }

            if (Regex("^\\d+\\.\\s+").containsMatchIn(line.trim())) {
                val items = mutableListOf<String>()
                while (i < lines.size && Regex("^\\d+\\.\\s+").containsMatchIn(lines[i].trim())) {
                    items.add(lines[i].trim().replace(Regex("^\\d+\\.\\s+"), "")); i++
                }
                Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                    items.forEachIndexed { idx, item ->
                        Row { Text("${idx + 1}.  ", color = TextPrimary); InlineMarkdownText(text = item, color = TextPrimary) }
                    }
                }
                continue
            }

            if (line.isBlank()) { i++; continue }

            val paraLines = mutableListOf<String>()
            while (
                i < lines.size && lines[i].isNotBlank() &&
                !lines[i].trim().startsWith("```") && !lines[i].trim().startsWith(">") &&
                !(lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* ")) &&
                !Regex("^\\d+\\.\\s+").containsMatchIn(lines[i].trim()) &&
                !Regex("^#{1,3}\\s+").containsMatchIn(lines[i])
            ) { paraLines.add(lines[i]); i++ }
            InlineMarkdownText(text = paraLines.joinToString("\n"), color = TextPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentIndicator(count: Int, onClick: () -> Unit) {
    Surface(color = CharcoalInput, shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(bottom = 4.dp).combinedClickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Text(text = "$count attached file${if (count == 1) "" else "s"}", color = TextSecondary, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun AttachmentListDialog(attachments: List<Attachment>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CharcoalElevated,
        title = { Text("Attachments", color = TextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.widthIn(max = 280.dp)) {
                items(attachments) { attachment ->
                    Text(text = attachment.name, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Mint) } }
    )
}