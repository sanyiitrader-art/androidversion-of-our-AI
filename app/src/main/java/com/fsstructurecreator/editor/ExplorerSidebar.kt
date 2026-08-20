package com.fsstructurecreator.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.fsstructurecreator.ui.CharcoalElevated
import com.fsstructurecreator.ui.CharcoalInput
import com.fsstructurecreator.ui.DangerColor
import com.fsstructurecreator.ui.Mint
import com.fsstructurecreator.ui.MintSoft
import com.fsstructurecreator.ui.TextPrimary
import com.fsstructurecreator.ui.TextSecondary
import com.fsstructurecreator.ui.TextTertiary
import kotlinx.coroutines.delay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExplorerSidebar(
    visible: Boolean,
    tree: WorkspaceNode?,
    selectedUri: String?,
    onToggleExpand: (String) -> Unit,
    onSelectNode: (WorkspaceNode) -> Unit,
    onCreateFile: () -> Unit,
    onCreateFolder: () -> Unit,
    onRenameRequest: (WorkspaceNode) -> Unit,
    onDeleteRequest: (WorkspaceNode) -> Unit,
    inlineEdit: InlineEditState?,
    onSubmitInlineEdit: (String) -> Unit,
    onCancelInlineEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    var pendingDeleteNode by remember { mutableStateOf<WorkspaceNode?>(null) }
    val listState = rememberLazyListState()

    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { -it }),
        exit = slideOutHorizontally(targetOffsetX = { -it })
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.9f)
                    .background(CharcoalElevated)
                    // No-op tap absorber over the ENTIRE panel area --
                    // without this, any blank space inside the panel
                    // (e.g. the toolbar row's empty left portion, which
                    // sits directly above the rail's Menu button) had
                    // no pointer-input handler of its own, so Compose
                    // let those taps fall straight through to whatever
                    // was rendered underneath. Individual buttons/rows
                    // inside the panel still get first claim on their
                    // own bounds (Compose gives nested clickables
                    // priority), so this only catches the gaps.
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* absorb; blank panel space should do nothing */ }
                    .padding(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onCreateFolder) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder", tint = TextPrimary)
                    }
                    IconButton(onClick = onCreateFile) {
                        Icon(Icons.Filled.NoteAdd, contentDescription = "New File", tint = TextPrimary)
                    }
                }

                if (tree == null) {
                    Text(
                        "Loading...",
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    val visibleNodes = flattenVisible(tree)
                    val horizontalScrollState = rememberScrollState()

                    LaunchedEffect(inlineEdit) {
                        val edit = inlineEdit
                        if (edit != null && edit.isRename) {
                            val index = visibleNodes.indexOfFirst { it.uri == edit.existingUri }
                            if (index >= 0) {
                                horizontalScrollState.animateScrollTo(0)
                                listState.animateScrollToItem(index)
                            }
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        items(visibleNodes, key = { it.uri }) { node ->
                            val isRenamingThisNode =
                                inlineEdit != null && inlineEdit.isRename && inlineEdit.existingUri == node.uri

                            if (isRenamingThisNode) {
                                InlineNameField(
                                    depth = node.depth,
                                    initialText = inlineEdit!!.initialText,
                                    error = inlineEdit.error,
                                    selectNameOnlyForFile = !inlineEdit.isDirectory,
                                    onSubmit = onSubmitInlineEdit,
                                    onCancel = onCancelInlineEdit
                                )
                            } else {
                                ExplorerRow(
                                    node = node,
                                    isSelected = node.uri == selectedUri,
                                    onClick = { onSelectNode(node) },
                                    onLongPressRename = { onRenameRequest(node) },
                                    onLongPressNewFile = { if (node.isDirectory) { onSelectNode(node); onCreateFile() } },
                                    onLongPressDelete = { pendingDeleteNode = node }
                                )
                            }

                            if (inlineEdit != null && !inlineEdit.isRename && inlineEdit.parentUri == node.uri) {
                                InlineNameField(
                                    depth = node.depth + 1,
                                    initialText = "",
                                    error = inlineEdit.error,
                                    onSubmit = onSubmitInlineEdit,
                                    onCancel = onCancelInlineEdit
                                )
                            }
                        }
                    }

                    if (inlineEdit != null && !inlineEdit.isRename && inlineEdit.parentUri == tree.uri) {
                        InlineNameField(
                            depth = 1,
                            initialText = "",
                            error = inlineEdit.error,
                            onSubmit = onSubmitInlineEdit,
                            onCancel = onCancelInlineEdit
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { onDismiss() }
            )
        }
    }

    val deleteNode = pendingDeleteNode
    if (deleteNode != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteNode = null },
            containerColor = CharcoalElevated,
            title = { Text("Delete \"${deleteNode.name}\"?", color = TextPrimary) },
            text = {
                Text(
                    if (deleteNode.isDirectory) "This folder and everything inside it will be deleted. This can't be undone."
                    else "This can't be undone.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRequest(deleteNode)
                    pendingDeleteNode = null
                }) {
                    Text("Delete", color = DangerColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteNode = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

private fun flattenVisible(node: WorkspaceNode): List<WorkspaceNode> {
    val result = mutableListOf<WorkspaceNode>()
    fun walk(n: WorkspaceNode, includeSelf: Boolean) {
        if (includeSelf) result.add(n)
        if (n.isDirectory && n.isExpanded) {
            n.children.forEach { walk(it, true) }
        }
    }
    walk(node, includeSelf = false)
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExplorerRow(
    node: WorkspaceNode,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPressRename: () -> Unit,
    onLongPressNewFile: () -> Unit,
    onLongPressDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) MintSoft else Color.Transparent, RoundedCornerShape(4.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showMenu = true }
                )
                .padding(start = (node.depth * 16).dp, top = 6.dp, bottom = 6.dp, end = 8.dp)
        ) {
            if (node.isDirectory) {
                Icon(
                    if (node.isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Icon(Icons.Filled.Folder, contentDescription = null, tint = Mint, modifier = Modifier.size(16.dp).padding(start = 4.dp))
            } else {
                Icon(Icons.Filled.InsertDriveFile, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp).padding(start = 20.dp))
            }
            Text(
                text = node.name,
                color = if (isSelected) Mint else TextPrimary,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("New File", color = if (node.isDirectory) TextPrimary else TextTertiary) },
                enabled = node.isDirectory,
                onClick = { showMenu = false; onLongPressNewFile() }
            )
            DropdownMenuItem(
                text = { Text("Rename", color = TextPrimary) },
                onClick = { showMenu = false; onLongPressRename() }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = DangerColor) },
                onClick = { showMenu = false; onLongPressDelete() }
            )
        }
    }
}

@Composable
private fun InlineNameField(
    depth: Int,
    initialText: String,
    error: CreationErrorState,
    selectNameOnlyForFile: Boolean = false,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    val nameWithoutExt = if (selectNameOnlyForFile && initialText.contains('.')) {
        initialText.substringBeforeLast('.')
    } else initialText

    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = if (selectNameOnlyForFile) TextRange(0, nameWithoutExt.length) else TextRange(0, initialText.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(150)
        focusRequester.requestFocus()
    }

    LaunchedEffect(error) {
        if (error == CreationErrorState.DUPLICATE_NAME) {
            repeat(4) {
                shakeOffset.animateTo(8f, tween(60))
                shakeOffset.animateTo(-8f, tween(60))
            }
            shakeOffset.animateTo(0f, tween(60))
        }
    }

    val borderColor = if (error == CreationErrorState.DUPLICATE_NAME) DangerColor else Mint

    fun submit() {
        val trimmed = fieldValue.text.trim()
        if (trimmed.isNotEmpty()) onSubmit(trimmed)
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { fieldValue = it },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = CharcoalInput,
            unfocusedContainerColor = CharcoalInput,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp)
            .offset(x = shakeOffset.value.dp)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .focusRequester(focusRequester)
    )
}