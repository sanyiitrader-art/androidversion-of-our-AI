package com.fsstructurecreator.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.fsstructurecreator.data.ActionKind
import com.fsstructurecreator.data.Attachment
import com.fsstructurecreator.data.AttachmentKind
import com.fsstructurecreator.data.ChatMessage
import com.fsstructurecreator.data.Conversation
import com.fsstructurecreator.data.ConversationStore
import com.fsstructurecreator.data.ConversationSummary
import com.fsstructurecreator.data.FsOperation
import com.fsstructurecreator.data.FsRequest
import com.fsstructurecreator.data.MessageRole
import com.fsstructurecreator.data.SettingsStore
import com.fsstructurecreator.ai.GeminiClient
import com.fsstructurecreator.fs.FilesystemEngine
import kotlinx.coroutines.launch
import java.util.UUID

private const val PREFS_NAME = "fs_prefs"
private const val KEY_SELECTED_FOLDER_URI = "selected_folder_uri"

private fun newMessage(role: MessageRole, content: String): ChatMessage {
    return ChatMessage(
        id = UUID.randomUUID().toString(),
        role = role,
        content = content,
        createdAt = System.currentTimeMillis().toString()
    )
}

private fun deriveTitle(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "New chat"
    return if (trimmed.length > 40) trimmed.take(40) + "..." else trimmed
}

private fun summarizeForAi(results: List<com.fsstructurecreator.data.FsOperationResult>): String {
    val lines = mutableListOf<String>()
    for (r in results) {
        for (d in r.createdDirectories) lines.add("Created directory: ${d}")
        for (f in r.createdFiles) lines.add("Created file: ${f}")
        for (e in r.errors) lines.add("Failed (${e.error}) for ${e.itemKind} \"${e.path}\"")
    }
    return if (lines.isEmpty()) "[Execution result]\nNo items were created."
    else "[Execution result]\n" + lines.joinToString("\n")
}

private fun currentlyValidFolderUri(context: Context, prefs: android.content.SharedPreferences): String? {
    val saved = prefs.getString(KEY_SELECTED_FOLDER_URI, null) ?: return null
    val stillGranted = context.contentResolver.persistedUriPermissions.any {
        it.uri.toString() == saved && it.isWritePermission
    }
    if (!stillGranted) {
        prefs.edit().remove(KEY_SELECTED_FOLDER_URI).apply()
        return null
    }
    return saved
}

@Composable
fun ChatScreen(
    onOpenEditor: () -> Unit,
    onSwipeToEditor: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val conversationStore = remember { ConversationStore(context) }
    val settingsStore = remember { SettingsStore(context) }
    val geminiClient = remember { GeminiClient { settingsStore.getApiKey() } }
    val fsEngine = remember { FilesystemEngine(context) }
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var conversation by remember { mutableStateOf<Conversation?>(null) }
    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var sidebarOpen by remember { mutableStateOf(false) }
    var editApiOpen by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var pendingAttachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var pendingFsRequest by remember { mutableStateOf<FsRequest?>(null) }
    val listState = rememberLazyListState()

    fun refreshConversations() {
        conversations = if (searchQuery.isBlank()) conversationStore.listConversations()
        else conversationStore.searchConversations(searchQuery)
    }

    LaunchedEffect(Unit) {
        conversation = conversationStore.createConversation()
        refreshConversations()
    }
    LaunchedEffect(searchQuery) { refreshConversations() }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            prefs.edit().putString(KEY_SELECTED_FOLDER_URI, uri.toString()).apply()

            val request = pendingFsRequest
            if (request != null) {
                pendingFsRequest = null
                scope.launch {
                    executeAndRespond(
                        request, uri.toString(), fsEngine, geminiClient,
                        conversation, conversationStore
                    ) { conversation = it }
                }
            }
        }
    }

    val attachLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        val newOnes = uris.mapNotNull { uri ->
            val name = queryFileName(context, uri) ?: return@mapNotNull null
            val ext = name.substringAfterLast('.', "").lowercase()
            val kind = when (ext) {
                "txt" -> AttachmentKind.TXT
                "md" -> AttachmentKind.MD
                else -> return@mapNotNull null
            }
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
            Attachment(name, kind, content)
        }
        pendingAttachments = pendingAttachments + newOnes
    }

    fun handleSend(text: String) {
        val convo = conversation ?: return
        if (sending) return

        val displayText = if (pendingAttachments.isNotEmpty()) {
            text + (if (text.isNotEmpty()) "\n" else "") +
                "[Attached: ${pendingAttachments.joinToString(", ") { it.name }}]"
        } else text

        val userMsg = newMessage(MessageRole.USER, displayText)
        val historyBefore = convo.messages
        val isFirst = convo.messages.isEmpty()

        var working = convo.copy(
            title = if (isFirst) deriveTitle(text) else convo.title,
            messages = convo.messages + userMsg,
            updatedAt = System.currentTimeMillis().toString()
        )
        conversation = working
        val attachmentsForSend = pendingAttachments
        pendingAttachments = emptyList()
        sending = true

        scope.launch {
            try {
                val turn = geminiClient.sendTurn(historyBefore, text, attachmentsForSend)
                var assistantText = turn.replyText

                if (turn.fsRequest != null) {
                    val savedFolder = currentlyValidFolderUri(context, prefs)
                    if (savedFolder == null) {
                        pendingFsRequest = turn.fsRequest
                        folderPickerLauncher.launch(null)
                        sending = false
                        val askMsg = newMessage(MessageRole.ASSISTANT, turn.replyText)
                        working = working.copy(messages = working.messages + askMsg)
                        conversation = working
                        conversationStore.saveConversation(working)
                        refreshConversations()
                        return@launch
                    }

                    val resolvedOps = turn.fsRequest.operations.map { op ->
                        op.copy(rootPath = if (op.rootPath == "SELECTED_FOLDER") savedFolder else op.rootPath)
                    }
                    val results = resolvedOps.map { fsEngine.executeOperation(it) }
                    val summary = summarizeForAi(results)

                    val followUpHistory = historyBefore + userMsg + newMessage(MessageRole.ASSISTANT, turn.replyText)
                    val followUp = geminiClient.sendTurn(followUpHistory, summary, emptyList())
                    assistantText = followUp.replyText
                }

                val assistantMsg = newMessage(MessageRole.ASSISTANT, assistantText)
                working = working.copy(
                    messages = working.messages + assistantMsg,
                    updatedAt = System.currentTimeMillis().toString()
                )
                conversation = working
                conversationStore.saveConversation(working)
                refreshConversations()
            } catch (e: Exception) {
                val errorMsg = newMessage(MessageRole.ASSISTANT, e.message ?: "Something went wrong.")
                working = working.copy(messages = working.messages + errorMsg)
                conversation = working
                conversationStore.saveConversation(working)
            } finally {
                sending = false
            }
        }
    }

    LaunchedEffect(conversation?.messages?.size) {
        val size = conversation?.messages?.size ?: 0
        if (size > 0) listState.animateScrollToItem(size - 1)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .pointerInput(sidebarOpen) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (sidebarOpen) {
                            if (totalDrag < -100f) sidebarOpen = false
                        } else {
                            if (totalDrag > 100f) sidebarOpen = true
                            else if (totalDrag < -100f) onSwipeToEditor()
                        }
                        totalDrag = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = { sidebarOpen = true }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = TextPrimary)
                }
                Text("FS Structure Creator", color = TextPrimary, modifier = Modifier.padding(start = 4.dp))
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenEditor) {
                    Icon(Icons.Filled.InsertDriveFile, contentDescription = "Editor", tint = TextPrimary)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                items(conversation?.messages ?: emptyList()) { msg ->
                    MessageBubble(msg)
                }
            }

            MessageInputBar(
                enabled = !sending,
                onAttachClick = { attachLauncher.launch(arrayOf("text/plain", "text/markdown")) },
                pendingAttachments = pendingAttachments,
                onSend = { handleSend(it) }
            )
        }

        AnimatedVisibility(
            visible = sidebarOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it })
        ) {
            Sidebar(
                conversations = conversations,
                activeConversationId = conversation?.id,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSelectConversation = { id ->
                    conversation = conversationStore.getConversation(id)
                    sidebarOpen = false
                },
                onNewChat = {
                    conversation = conversationStore.createConversation()
                    refreshConversations()
                    sidebarOpen = false
                },
                onEditApi = { editApiOpen = true },
                onSelectFolder = {
                    pendingFsRequest = null
                    folderPickerLauncher.launch(null)
                    sidebarOpen = false
                },
                onScrimClick = { sidebarOpen = false }
            )
        }
    }

    if (editApiOpen) {
        ApiKeyDialog(
            keyAlreadySaved = settingsStore.hasApiKey(),
            onDismiss = { editApiOpen = false },
            onSave = { key ->
                settingsStore.setApiKey(key)
                editApiOpen = false
            }
        )
    }
}

private fun queryFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) return it.getString(nameIndex)
    }
    return uri.lastPathSegment
}

private suspend fun executeAndRespond(
    request: FsRequest,
    folderUri: String,
    fsEngine: FilesystemEngine,
    geminiClient: GeminiClient,
    conversation: Conversation?,
    conversationStore: ConversationStore,
    onUpdate: (Conversation) -> Unit
) {
    val convo = conversation ?: return
    val resolvedOps = request.operations.map { op ->
        op.copy(rootPath = if (op.rootPath == "SELECTED_FOLDER") folderUri else op.rootPath)
    }
    val results = resolvedOps.map { fsEngine.executeOperation(it) }
    val summary = summarizeForAi(results)
    val followUp = geminiClient.sendTurn(convo.messages, summary, emptyList())
    val assistantMsg = newMessage(MessageRole.ASSISTANT, followUp.replyText)
    val updated = convo.copy(messages = convo.messages + assistantMsg)
    conversationStore.saveConversation(updated)
    onUpdate(updated)
}