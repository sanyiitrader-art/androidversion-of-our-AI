package com.fsstructurecreator.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.unit.dp
import com.fsstructurecreator.ui.CharcoalInput
import com.fsstructurecreator.ui.Mint
import com.fsstructurecreator.ui.MintSoft
import com.fsstructurecreator.ui.TextPrimary
import com.fsstructurecreator.ui.TextSecondary
import com.fsstructurecreator.ui.TextTertiary

@Composable
fun EditorTopBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    tree: WorkspaceNode?,
    openFileContent: String?,
    currentFileName: String?,
    searchMode: WorkspaceSearchMode,
    onSearchModeChange: (WorkspaceSearchMode) -> Unit,
    onSelectFileResult: (String) -> Unit,
    onSelectTextResult: (TextSearchResult) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }

    fun flattenFiles(node: WorkspaceNode?, acc: MutableList<WorkspaceNode>) {
        if (node == null) return
        if (!node.isDirectory) acc.add(node)
        node.children.forEach { flattenFiles(it, acc) }
    }

    val fileResults: List<FileSearchResult> = if (searchMode == WorkspaceSearchMode.FILE_NAME && query.isNotBlank()) {
        val all = mutableListOf<WorkspaceNode>()
        flattenFiles(tree, all)
        all.filter { it.name.startsWith(query, ignoreCase = true) }
            .take(50)
            .map { FileSearchResult(it.uri, it.name, it.parentUri ?: "") }
    } else emptyList()

    val textResults: List<TextSearchResult> = if (searchMode == WorkspaceSearchMode.TEXT_IN_FILE && query.isNotBlank() && openFileContent != null) {
        val lines = openFileContent.lines()
        val results = mutableListOf<TextSearchResult>()
        lines.forEachIndexed { index, line ->
            val idx = line.indexOf(query, ignoreCase = true)
            if (idx >= 0) {
                results.add(TextSearchResult(index + 1, line, idx, idx + query.length))
            }
        }
        results
    } else emptyList()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = if (canGoBack) TextPrimary else TextTertiary)
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.Filled.ArrowForward, contentDescription = "Forward", tint = if (canGoForward) TextPrimary else TextTertiary)
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Workspace", color = TextTertiary) },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = CharcoalInput,
                    unfocusedContainerColor = CharcoalInput,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp)
                    .background(CharcoalInput, RoundedCornerShape(8.dp))
                    .onFocusEvent { focused = it.isFocused }
            )
        }

        if (currentFileName != null) {
            Text(
                text = currentFileName,
                color = TextSecondary,
                modifier = Modifier.padding(start = 52.dp, top = 4.dp)
            )
        }

        if (focused && query.isBlank()) {
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable {
                        onSearchModeChange(
                            if (searchMode == WorkspaceSearchMode.TEXT_IN_FILE) WorkspaceSearchMode.FILE_NAME
                            else WorkspaceSearchMode.TEXT_IN_FILE
                        )
                    }
                    .background(
                        if (searchMode == WorkspaceSearchMode.TEXT_IN_FILE) MintSoft else androidx.compose.ui.graphics.Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "Search Text",
                    color = if (searchMode == WorkspaceSearchMode.TEXT_IN_FILE) Mint else TextSecondary
                )
            }
        }

        if (query.isNotBlank()) {
            if (searchMode == WorkspaceSearchMode.FILE_NAME) {
                if (fileResults.isEmpty()) {
                    Text("No Result Found", color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        items(fileResults) { result ->
                            Text(
                                text = result.name,
                                color = TextPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectFileResult(result.uri)
                                        query = ""
                                        focused = false
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            } else {
                if (textResults.isEmpty()) {
                    Text("No Result Found", color = TextSecondary, modifier = Modifier.padding(top = 6.dp))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(120.dp)
                    ) {
                        items(textResults) { result ->
                            Text(
                                text = "L${result.lineNumber}: ${result.lineText.trim()}",
                                color = TextPrimary,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onSelectTextResult(result)
                                        query = ""
                                        focused = false
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}