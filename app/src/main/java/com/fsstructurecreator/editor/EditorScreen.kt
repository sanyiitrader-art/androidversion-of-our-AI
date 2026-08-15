package com.fsstructurecreator.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fsstructurecreator.ui.CharcoalBg
import com.fsstructurecreator.ui.CharcoalElevated
import com.fsstructurecreator.ui.Mint
import com.fsstructurecreator.ui.TextPrimary
import com.fsstructurecreator.ui.TextSecondary
import com.fsstructurecreator.ui.TextTertiary

data class InlineEditState(
    val parentUri: String,
    val isRename: Boolean,
    val isDirectory: Boolean,
    val existingUri: String? = null,
    val initialText: String = "",
    val error: CreationErrorState = CreationErrorState.NONE
)

@Composable
fun EditorScreen(
    session: EditorSessionState,
    onSwipeToAi: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WorkspaceStore(context) }

    var explorerOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var inlineEdit by remember { mutableStateOf<InlineEditState?>(null) }
    var searchMode by remember { mutableStateOf(WorkspaceSearchMode.FILE_NAME) }
    var highlightRequest by remember { mutableStateOf<TextSearchResult?>(null) }

    fun updateNode(node: WorkspaceNode, targetUri: String, transform: (WorkspaceNode) -> WorkspaceNode): WorkspaceNode {
        if (node.uri == targetUri) return transform(node)
        if (node.children.isEmpty()) return node
        return node.copy(children = node.children.map { updateNode(it, targetUri, transform) })
    }

    fun refreshTree(preserveExpansion: Boolean = true) {
        val root = session.workspaceRoot ?: return
        val newTree = store.loadTree(root) ?: return
        if (!preserveExpansion || session.tree == null) {
            session.tree = newTree
            return
        }
        val expandedUris = mutableSetOf<String>()
        fun collectExpanded(n: WorkspaceNode) {
            if (n.isExpanded) expandedUris.add(n.uri)
            n.children.forEach { collectExpanded(it) }
        }
        session.tree?.let { collectExpanded(it) }
        fun applyExpansion(n: WorkspaceNode): WorkspaceNode {
            val expanded = expandedUris.contains(n.uri)
            return n.copy(isExpanded = expanded, children = n.children.map { applyExpansion(it) })
        }
        session.tree = applyExpansion(newTree)
    }

    fun setWorkspaceRoot(uri: String) {
        session.workspaceRoot = uri
        session.selectedUri = null
        session.openFile = null
        session.unsupportedFileMessage = null
        session.navHistory = NavigationHistory()
        session.unsavedEdits = emptyMap()
        session.tree = store.loadTree(uri)?.copy(isExpanded = true)
    }

    fun openFileAt(uri: String, addToHistory: Boolean = true) {
        fun findName(n: WorkspaceNode?): String? {
            if (n == null) return null
            if (n.uri == uri) return n.name
            for (c in n.children) findName(c)?.let { return it }
            return null
        }
        val name = findName(session.tree) ?: uri.substringAfterLast('/')

        if (!session.unsavedEdits.containsKey(uri) && store.isLikelyBinary(uri)) {
            session.unsupportedFileMessage = "\"$name\" doesn't look like a text file and can't be opened here."
            session.openFile = null
            session.selectedUri = uri
            explorerOpen = false
            return
        }

        val content = session.unsavedEdits[uri] ?: store.readFile(uri)
        session.unsupportedFileMessage = null
        session.openFile = OpenFile(uri, name, content, isDirty = session.unsavedEdits.containsKey(uri))
        session.selectedUri = uri
        explorerOpen = false
        if (addToHistory) session.navHistory = session.navHistory.navigateTo(uri)
    }

    fun toggleExpand(uri: String) {
        session.tree = session.tree?.let { updateNode(it, uri, { n -> n.copy(isExpanded = !n.isExpanded) }) }
    }

    fun expandUri(uri: String) {
        session.tree = session.tree?.let { updateNode(it, uri, { n -> n.copy(isExpanded = true) }) }
    }

    fun selectNode(node: WorkspaceNode) {
        if (node.isDirectory) {
            session.selectedUri = node.uri
            toggleExpand(node.uri)
        } else {
            openFileAt(node.uri)
        }
    }

    fun resolveCreationParent(): String {
        val sel = session.selectedUri
        if (sel == null) return session.workspaceRoot ?: ""
        fun findNode(n: WorkspaceNode?): WorkspaceNode? {
            if (n == null) return null
            if (n.uri == sel) return n
            for (c in n.children) findNode(c)?.let { return it }
            return null
        }
        val node = findNode(session.tree) ?: return session.workspaceRoot ?: ""
        return if (node.isDirectory) node.uri else (node.parentUri ?: session.workspaceRoot ?: "")
    }

    fun beginCreate(isDirectory: Boolean) {
        val parent = resolveCreationParent()
        expandUri(parent)
        inlineEdit = InlineEditState(parentUri = parent, isRename = false, isDirectory = isDirectory)
    }

    fun beginRename(node: WorkspaceNode) {
        inlineEdit = InlineEditState(
            parentUri = node.parentUri ?: "",
            isRename = true,
            isDirectory = node.isDirectory,
            existingUri = node.uri,
            initialText = node.name
        )
    }

    fun submitInlineEdit(name: String) {
        val edit = inlineEdit ?: return
        if (edit.isRename && edit.existingUri != null) {
            val ok = store.rename(edit.existingUri, name)
            if (ok) {
                inlineEdit = null
                refreshTree()
            } else {
                inlineEdit = edit.copy(error = CreationErrorState.DUPLICATE_NAME)
            }
            return
        }

        val result = if (edit.isDirectory) {
            store.createFolder(edit.parentUri, name)
        } else {
            store.createFile(edit.parentUri, name)
        }

        when (result) {
            is WorkspaceStore.CreateResult.Success -> {
                inlineEdit = null
                refreshTree()
                session.selectedUri = result.uri
            }
            WorkspaceStore.CreateResult.DuplicateName -> {
                inlineEdit = edit.copy(error = CreationErrorState.DUPLICATE_NAME)
            }
            WorkspaceStore.CreateResult.Failure -> {
                inlineEdit = null
            }
        }
    }

    /** Checks whether targetUri is deletedUri itself or lives anywhere
     *  underneath it -- used so deleting a folder correctly clears the
     *  open file / unsaved edits for anything that was inside it. */
    fun isUnderOrEqual(targetUri: String, deletedNode: WorkspaceNode): Boolean {
        if (targetUri == deletedNode.uri) return true
        fun search(n: WorkspaceNode): Boolean {
            if (n.uri == targetUri) return true
            return n.children.any { search(it) }
        }
        return deletedNode.children.any { search(it) }
    }

    fun deleteNode(node: WorkspaceNode) {
        val ok = store.delete(node.uri)
        if (!ok) return

        val openUri = session.openFile?.uri
        if (openUri != null && isUnderOrEqual(openUri, node)) {
            session.openFile = null
            session.unsupportedFileMessage = null
        }
        session.unsavedEdits = session.unsavedEdits.filterKeys { !isUnderOrEqual(it, node) }
        if (session.selectedUri != null && isUnderOrEqual(session.selectedUri!!, node)) {
            session.selectedUri = node.parentUri
        }
        refreshTree()
    }

    fun onContentChange(newContent: String) {
        val current = session.openFile ?: return
        session.openFile = current.copy(content = newContent, isDirty = true)
        session.unsavedEdits = session.unsavedEdits + (current.uri to newContent)
        if (session.autoSave) {
            store.writeFile(current.uri, newContent)
            session.unsavedEdits = session.unsavedEdits - current.uri
            session.openFile = session.openFile?.copy(isDirty = false)
        }
    }

    fun saveCurrent() {
        val current = session.openFile ?: return
        store.writeFile(current.uri, current.content)
        session.unsavedEdits = session.unsavedEdits - current.uri
        session.openFile = current.copy(isDirty = false)
    }

    fun saveAll() {
        session.unsavedEdits.forEach { (uri, content) -> store.writeFile(uri, content) }
        session.unsavedEdits = emptyMap()
        session.openFile = session.openFile?.copy(isDirty = false)
    }

    fun startNewWorkspace(createFileNotFolder: Boolean) {
        val parentForNewRoot = session.workspaceRoot ?: return
        val uniqueName = store.uniqueWorkspaceFolderName(parentForNewRoot)
        val result = store.createFolder(parentForNewRoot, uniqueName)
        if (result is WorkspaceStore.CreateResult.Success) {
            setWorkspaceRoot(parentForNewRoot)
            session.tree = store.loadTree(parentForNewRoot)?.copy(isExpanded = true)
            session.selectedUri = result.uri
            explorerOpen = true
            beginCreate(isDirectory = !createFileNotFolder)
        }
    }

    val openFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            setWorkspaceRoot(uri.toString())
        }
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (totalDrag > 100f) onSwipeToAi()
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount }
                )
            }
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            EditorRail(
                onMenuClick = { menuOpen = true },
                onExplorerClick = { if (session.workspaceRoot != null) explorerOpen = true }
            )

            Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                Box(modifier = Modifier.statusBarsPadding())

                EditorTopBar(
                    canGoBack = session.navHistory.canGoBack,
                    canGoForward = session.navHistory.canGoForward,
                    onBack = {
                        session.navHistory = session.navHistory.goBack()
                        session.navHistory.current?.let { openFileAt(it, addToHistory = false) }
                    },
                    onForward = {
                        session.navHistory = session.navHistory.goForward()
                        session.navHistory.current?.let { openFileAt(it, addToHistory = false) }
                    },
                    tree = session.tree,
                    openFileContent = session.openFile?.content,
                    currentFileName = session.openFile?.name,
                    searchMode = searchMode,
                    onSearchModeChange = { searchMode = it },
                    onSelectFileResult = { openFileAt(it) },
                    onSelectTextResult = { result -> highlightRequest = result }
                )

                when {
                    session.workspaceRoot == null -> EditorStartScreen(
                        onNewFile = { openFolderLauncher.launch(null) },
                        onOpenFile = { openFileLauncher.launch(arrayOf("text/*")) },
                        onOpenFolder = { openFolderLauncher.launch(null) }
                    )
                    session.unsupportedFileMessage != null -> UnsupportedFileScreen(session.unsupportedFileMessage!!)
                    else -> TextEditorView(
                        openFile = session.openFile,
                        onContentChange = { onContentChange(it) },
                        highlightRequest = highlightRequest,
                        onHighlightConsumed = { highlightRequest = null }
                    )
                }
            }
        }

        ExplorerSidebar(
            visible = explorerOpen && session.tree != null,
            tree = session.tree,
            selectedUri = session.selectedUri,
            onToggleExpand = { toggleExpand(it) },
            onSelectNode = { selectNode(it) },
            onCreateFile = { beginCreate(isDirectory = false) },
            onCreateFolder = { beginCreate(isDirectory = true) },
            onRenameRequest = { beginRename(it) },
            onDeleteRequest = { deleteNode(it) },
            inlineEdit = inlineEdit,
            onSubmitInlineEdit = { submitInlineEdit(it) },
            onCancelInlineEdit = { inlineEdit = null },
            onDismiss = { explorerOpen = false }
        )

        EditorMenu(
            visible = menuOpen,
            autoSave = session.autoSave,
            onAutoSaveToggle = { session.autoSave = it },
            onNewFile = { menuOpen = false; startNewWorkspace(createFileNotFolder = true) },
            onNewFolder = { menuOpen = false; startNewWorkspace(createFileNotFolder = false) },
            onOpenFile = { menuOpen = false; openFileLauncher.launch(arrayOf("text/*")) },
            onOpenFolder = { menuOpen = false; openFolderLauncher.launch(null) },
            onSave = { menuOpen = false; saveCurrent() },
            onSaveAll = { menuOpen = false; saveAll() },
            onDismiss = { menuOpen = false }
        )
    }
}

@Composable
private fun EditorRail(
    onMenuClick: () -> Unit,
    onExplorerClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(56.dp)
            .background(CharcoalElevated)
            .statusBarsPadding()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = TextPrimary)
        }
        IconButton(onClick = onExplorerClick) {
            Icon(Icons.Filled.InsertDriveFile, contentDescription = "Explorer", tint = TextPrimary)
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { /* undefined per spec section 42 -- intentionally inert */ }) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextSecondary)
        }
    }
}

@Composable
private fun EditorStartScreen(
    onNewFile: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Start", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

        StartAction(Icons.Filled.NoteAdd, "New File...", onNewFile)
        StartAction(Icons.Filled.FileOpen, "Open File...", onOpenFile)
        StartAction(Icons.Filled.FolderOpen, "Open Folder...", onOpenFolder)

        Spacer(modifier = Modifier.padding(top = 16.dp))
        Text("Recent", color = TextPrimary, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)

        Row(modifier = Modifier.padding(top = 8.dp)) {
            Text("You have no recent folders, ", color = TextSecondary)
            Text(
                "open a folder",
                color = Mint,
                modifier = Modifier.clickable { onOpenFolder() }
            )
            Text(" to start.", color = TextSecondary)
        }
    }
}

@Composable
private fun UnsupportedFileScreen(message: String) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text(message, color = TextSecondary)
    }
}

@Composable
private fun StartAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Mint, modifier = Modifier.size(18.dp))
        Text(label, color = Mint, modifier = Modifier.padding(start = 10.dp))
    }
}