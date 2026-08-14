package com.fsstructurecreator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fsstructurecreator.data.ConversationSummary

@Composable
fun Sidebar(
    conversations: List<ConversationSummary>,
    activeConversationId: String?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onEditApi: () -> Unit,
    onSelectFolder: () -> Unit,
    onScrimClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(280.dp)
                .background(CharcoalElevated)
                .padding(10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search conversations", color = TextTertiary) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextTertiary) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CharcoalInput,
                    unfocusedContainerColor = CharcoalInput,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            SidebarActionButton(icon = Icons.Filled.Edit, label = "Edit API", onClick = onEditApi)
            SidebarActionButton(icon = Icons.Filled.Folder, label = "Select Folder", onClick = onSelectFolder)
            SidebarActionButton(icon = Icons.Filled.Add, label = "New Chat", onClick = onNewChat)

            LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                items(conversations) { convo ->
                    val isActive = convo.id == activeConversationId
                    Text(
                        text = convo.title.ifBlank { "Untitled" },
                        color = if (isActive) Mint else TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isActive) MintSoft else Color.Transparent,
                                RoundedCornerShape(6.dp)
                            )
                            .clickable { onSelectConversation(convo.id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // Tappable sliver of the chat area, visible while the sidebar
        // is open -- tapping it closes the sidebar (spec section 19).
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onScrimClick() }
        )
    }
}

@Composable
private fun SidebarActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
        Text(
            text = label,
            color = TextPrimary,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}