package com.fsstructurecreator.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsstructurecreator.data.Attachment
import com.fsstructurecreator.data.ChatMessage
import com.fsstructurecreator.data.MessageRole

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
        UserMessage(
            message = message,
            isLatest = isLatestUserMessage,
            clipboard = clipboard,
            onSaveEdit = onSaveEdit
        )
    } else {
        AiMessage(
            message = message,
            isLatest = isLatestAiMessage,
            clipboard = clipboard,
            onLike = onLike,
            onDislike = onDislike,
            onRetry = onRetry
        )
    }
}

@Composable
private fun UserMessage(
    message: ChatMessage,
    isLatest: Boolean,
    clipboard: ClipboardManager,
    onSaveEdit: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(message.id) { mutableStateOf(message.content) }
    var showAttachmentsDialog by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
        if (message.attachments.isNotEmpty()) {
            AttachmentIndicator(
                count = message.attachments.size,
                onClick = { showAttachmentsDialog = true }
            )
        }

        if (isEditing) {
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 280.dp)) {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MintSoft,
                        unfocusedContainerColor = MintSoft,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = {
                        editText = message.content
                        isEditing = false
                    }) {
                        Text("Discard", color = TextSecondary)
                    }
                    TextButton(onClick = {
                        isEditing = false
                        onSaveEdit(editText.trim())
                    }) {
                        Text("Save", color = Mint)
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Box {
                    Surface(
                        color = MintSoft,
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 4.dp
                        ),
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .pointerInput(message.id) {
                                detectTapGestures(
                                    onLongPress = { pressOffset ->
                                        menuOffset = DpOffset(
                                            x = pressOffset.x.toDp(),
                                            y = pressOffset.y.toDp() - 110.dp
                                        )
                                        showMenu = true
                                    }
                                )
                            }
                    ) {
                        Text(
                            text = message.content,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            overflow = TextOverflow.Clip,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        offset = menuOffset
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy", color = TextPrimary) },
                            onClick = {
                                showMenu = false
                                clipboard.setText(AnnotatedString(message.content))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit", color = if (isLatest) TextPrimary else TextTertiary) },
                            enabled = isLatest,
                            onClick = {
                                showMenu = false
                                editText = message.content
                                isEditing = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAttachmentsDialog) {
        AttachmentListDialog(
            attachments = message.attachments,
            onDismiss = { showAttachmentsDialog = false }
        )
    }
}

@Composable
private fun AiMessage(
    message: ChatMessage,
    isLatest: Boolean,
    clipboard: ClipboardManager,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onRetry: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        FormattedText(message.content)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
        ) {
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(message.content)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(15.dp))
            }
            IconButton(onClick = onLike, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.ThumbUp,
                    contentDescription = "Like",
                    tint = if (message.liked) Mint else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(onClick = onDislike, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.ThumbDown,
                    contentDescription = "Dislike",
                    tint = if (message.disliked) Mint else TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
            IconButton(
                onClick = onRetry,
                enabled = isLatest,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Retry",
                    tint = if (isLatest) TextSecondary else TextTertiary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/** Minimal markdown-style renderer for AI responses: fenced code
 *  blocks get a monospace box, "#"/"##"/"###" headings, "-"/"*"
 *  lists, everything else as paragraphs. Not a full markdown parser
 *  -- just enough to stop code blocks from rendering as plain text
 *  with literal backtick fences, which is what was happening before. */
@Composable
private fun FormattedText(text: String) {
    val lines = text.split("\n")
    var i = 0

    Column(modifier = Modifier.fillMaxWidth(0.9f).padding(horizontal = 4.dp)) {
        while (i < lines.size) {
            val line = lines[i]

            if (line.trim().startsWith("```")) {
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // skip closing fence
                Surface(
                    color = CharcoalInput,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Text(
                        text = codeLines.joinToString("\n"),
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                continue
            }

            val headingMatch = Regex("^(#{1,3})\\s+(.*)$").find(line)
            if (headingMatch != null) {
                val level = headingMatch.groupValues[1].length
                Text(
                    text = headingMatch.groupValues[2],
                    color = Mint,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (17 - level).sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                i++
                continue
            }

            if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
                val items = mutableListOf<String>()
                while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* "))) {
                    items.add(lines[i].trim().removePrefix("- ").removePrefix("* "))
                    i++
                }
                Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 2.dp)) {
                    items.forEach { item ->
                        Row {
                            Text("•  ", color = TextPrimary)
                            Text(item, color = TextPrimary)
                        }
                    }
                }
                continue
            }

            if (line.isBlank()) {
                i++
                continue
            }

            val paraLines = mutableListOf<String>()
            while (
                i < lines.size &&
                lines[i].isNotBlank() &&
                !lines[i].trim().startsWith("```") &&
                !(lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* ")) &&
                !Regex("^#{1,3}\\s+").containsMatchIn(lines[i])
            ) {
                paraLines.add(lines[i])
                i++
            }
            Text(
                text = paraLines.joinToString("\n"),
                color = TextPrimary,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachmentIndicator(count: Int, onClick: () -> Unit) {
    Surface(
        color = CharcoalInput,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .padding(bottom = 4.dp)
            .combinedClickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Filled.AttachFile, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Text(
                text = "$count attached file${if (count == 1) "" else "s"}",
                color = TextSecondary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun AttachmentListDialog(attachments: List<Attachment>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalElevated,
        title = { Text("Attachments", color = TextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.widthIn(max = 280.dp)) {
                items(attachments) { attachment ->
                    Text(
                        text = attachment.name,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Mint)
            }
        }
    )
}