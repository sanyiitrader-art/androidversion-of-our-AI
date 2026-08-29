package com.fsstructurecreator.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fsstructurecreator.data.Attachment

@Composable
fun MessageInputBar(
    enabled: Boolean,
    sending: Boolean,
    onStop: () -> Unit,
    onAttachClick: () -> Unit,
    pendingAttachments: List<Attachment>,
    onRemoveAttachment: (Attachment) -> Unit,
    onSend: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Surface(
        color = CharcoalInput,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(6.dp)) {
            if (pendingAttachments.isNotEmpty()) {
                val scrollState = androidx.compose.foundation.rememberScrollState()
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .androidx.compose.foundation.horizontalScroll(scrollState)
                        .padding(bottom = 6.dp)
                ) {
                    pendingAttachments.forEach { attachment ->
                        AttachmentChip(attachment = attachment, onRemove = { onRemoveAttachment(attachment) })
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttachClick, enabled = enabled && pendingAttachments.size < 20) {
                    Icon(Icons.Filled.Add, contentDescription = "Attach file", tint = TextSecondary)
                }

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    enabled = enabled,
                    placeholder = { androidx.compose.material3.Text("Message...", color = TextTertiary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                )

                // Send button stays clickable even while sending=true --
                // it becomes a real Stop control, not a disabled state,
                // since Pause must be able to actually cancel generation.
                if (sending) {
                    IconButton(onClick = onStop, modifier = Modifier.size(36.dp).padding(2.dp)) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop generating", tint = Mint)
                    }
                } else {
                    IconButton(
                        onClick = {
                            val trimmed = text.trim()
                            if (trimmed.isNotEmpty() || pendingAttachments.isNotEmpty()) {
                                onSend(trimmed)
                                text = ""
                            }
                        },
                        modifier = Modifier.size(36.dp).padding(2.dp)
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = "Send", tint = Mint)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Surface(
        color = CharcoalBg,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = Modifier.padding(end = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.androidx.compose.foundation.layout.widthIn(max = 160.dp)
                .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            androidx.compose.material3.Text(
                text = attachment.name,
                color = TextPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = "Remove ${attachment.name}", tint = TextSecondary)
            }
        }
    }
}