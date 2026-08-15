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
fun EditorScreen(onSwipeToAi: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { WorkspaceStore(context) }

    var workspaceRoot by remember { mutableStateOf<String?>(null) }
    var tree by remember { mutableStateOf<WorkspaceNode?>(null) }
    var openFile by remember { mutableStateOf<OpenFile?>(null) }
    var navHistory by remember { mutableStateOf(NavigationHistory()) }
    var explorerOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var autoSave by remember { mutableStateOf(false) }
    var selectedUri by remember { mutableStateOf<String?>(null) }
    var unsavedEdits by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var inlineEdit by remember { mutableStateOf<InlineEditState?>(null) }
    var searchMode by remember { mutableStateOf(WorkspaceSearchMode.FILE_NAME) }

    fun updateNode(node: WorkspaceNode, targetUri: String, transform: (WorkspaceNode) -> WorkspaceNode): WorkspaceNode {
        if (node.uri == targetUri) return transform(node)
        if (node.children.isEmpty()) return node
        return node.copy(children = node.children.map { updateNode(it, targetUri, transform) })
    }

    fun refreshTree(preserveExpansion: Boolean = true) {
        val root = workspaceRoot ?: return
        val newTree = store.loadTree(root) ?: return
        if (!preserveExpansion || tree == null) {
            tree = newTree
            return
        }
        val expandedUris = mutableSetOf<String>()
        fun collectExpanded(n: WorkspaceNode) {
            if (n.isExpanded) expandedUris.add(n.uri)
            n.children.forEach { collectExpanded(it) }
        }
        tree?.let { collectExpanded(it) }
        fun applyExpansion(n: WorkspaceNode): WorkspaceNode {
            val expanded = expandedUris.contains(n.uri)
            return n.copy(isExpanded = expanded, children = n.children.map { applyExpansion(it) })
        }
        tree = applyExpansion(newTree)
    }

    fun setWorkspaceRoot(uri: String) {
        workspaceRoot = uri
        selectedUri = null
        openFile = null
        navHistory = NavigationHistory()
        unsavedEdits = emptyMap()
        tree = store.loadTree(uri)?.copy(isExpanded = true)
    }

    fun openFileAt(uri: String, addToHistory: Boolean = true) {
        val content = unsavedEdits[uri] ?: store.readFile(uri)
        fun findName(n: WorkspaceNode?): String? {
            if (n == null) return null
            if (n.uri == uri) return n.name
            for (c in n.children) findName(c)?.let { return it }
            return null
        }
        val name = findName(tree) ?: uri.substringAfterLast('/')
        openFile = OpenFile(uri, name, content, isDirty = unsavedEdits.containsKey(uri))
        selectedUri = uri
        explorerOpen = false
        if (addToHistory) navHistory = navHistory.navigateTo(uri)
    }

    fun toggleExpand(uri: String) {
        tree = tree?.let { updateNode(it, uri, { n -> n.copy(isExpanded = !n.isExpanded) }) }
    }

    fun expandUri(uri: String) {
        tree = tree?.let { updateNode(it, uri, { n -> n.copy(isExpanded = true) }) }
    }

    fun selectNode(node: WorkspaceNode) {
        if (node.isDirectory) {
            selectedUri = node.uri
            toggleExpand(node.uri)
        } else {
            openFileAt(node.uri)
        }
    }

    fun resolveCreationParent(): String {
        val sel = selectedUri
        if (sel == null) return workspaceRoot ?: ""
        fun findNode(n: WorkspaceNode?): WorkspaceNode? {
            if (n == null) return null
            if (n.uri == sel) return n
            for (c in n.children) findNode(c)?.let { return it }
            return null
        }
        val node = findNode(tree) ?: return workspaceRoot ?: ""
        return if (node.isDirectory) node.uri else (node.parentUri ?: workspaceRoot ?: "")
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
                selectedUri = result.uri
            }
            WorkspaceStore.CreateResult.DuplicateName -> {
                inlineEdit = edit.copy(error = CreationErrorState.DUPLICATE_NAME)
            }
            WorkspaceStore.CreateResult.Failure -> {
                inlineEdit = null
            }
        }
    }

    fun onContentChange(newContent: String) {
        val current = openFile ?: return
        openFile = current.copy(content = newContent, isDirty = true)
        unsavedEdits = unsavedEdits + (current.uri to newContent)
        if (autoSave) {
            store.writeFile(current.uri, newContent)
            unsavedEdits = unsavedEdits - current.uri
            openFile = openFile?.copy(isDirty = false)
        }
    }

    fun saveCurrent() {
        val current = openFile ?: return
        store.writeFile(current.uri, current.content)
        unsavedEdits = unsavedEdits - current.uri
        openFile = current.copy(isDirty = false)
    }

    fun saveAll() {
        unsavedEdits.forEach { (uri, content) -> store.writeFile(uri, content) }
        unsavedEdits = emptyMap()
        openFile = openFile?.copy(isDirty = false)
    }

    fun startNewWorkspace(createFileNotFolder: Boolean) {
        val base = workspaceRoot
        val parentForNewRoot = base ?: return
        val uniqueName = store.uniqueWorkspaceFolderName(parentForNewRoot)
        val result = store.createFolder(parentForNewRoot, uniqueName)
        if (result is WorkspaceStore.CreateResult.Success) {
            setWorkspaceRoot(parentForNewRoot)
            tree = store.loadTree(parentForNewRoot)?.copy(isExpanded = true)
            selectedUri = result.uri
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
                onExplorerClick = { if (workspaceRoot != null) explorerOpen = true }
            )

            Column(modifier = Modifier.fillMaxSize().weight(1f)) {
                Box(modifier = Modifier.statusBarsPadding())

                EditorTopBar(
                    canGoBack = navHistory.canGoBack,
                    canGoForward = navHistory.canGoForward,
                    onBack = {
                        navHistory = navHistory.goBack()
                        navHistory.current?.let { openFileAt(it, addToHistory = false) }
                    },
                    onForward = {
                        navHistory = navHistory.goForward()
                        navHistory.current?.let { openFileAt(it, addToHistory = false) }
                    },
                    tree = tree,
                    openFileContent = openFile?.content,
                    searchMode = searchMode,
                    onSearchModeChange = { searchMode = it },
                    onSelectFileResult = { openFileAt(it) },
                    onSelectTextResult = { }
                )

                if (workspaceRoot == null) {
                    EditorStartScreen(
                        onNewFile = { openFolderLauncher.launch(null) },
                        onOpenFile = { openFileLauncher.launch(arrayOf("text/*")) },
                        onOpenFolder = { openFolderLauncher.launch(null) }
                    )
                } else {
                    TextEditorView(
                        openFile = openFile,
                        onContentChange = { onContentChange(it) }
                    )
                }
            }
        }

        ExplorerSidebar(
            visible = explorerOpen && tree != null,
            tree = tree,
            selectedUri = selectedUri,
            onToggleExpand = { toggleExpand(it) },
            onSelectNode = { selectNode(it) },
            onCreateFile = { beginCreate(isDirectory = false) },
            onCreateFolder = { beginCreate(isDirectory = true) },
            onRenameRequest = { beginRename(it) },
            inlineEdit = inlineEdit,
            onSubmitInlineEdit = { submitInlineEdit(it) },
            onCancelInlineEdit = { inlineEdit = null },
            onDismiss = { explorerOpen = false }
        )

        EditorMenu(
            visible = menuOpen,
            autoSave = autoSave,
            onAutoSaveToggle = { autoSave = it },
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