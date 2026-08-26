package com.fsstructurecreator.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
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
import com.fsstructurecreator.data.FsOperationResult
import com.fsstructurecreator.data.FsRequest
import com.fsstructurecreator.data.MessageRole
import com.fsstructurecreator.data.SettingsStore
import com.fsstructurecreator.ai.GeminiClient
import com.fsstructurecreator.fs.FilesystemEngine
import kotlinx.coroutines.launch
import java.util.UUID

private const val PREFS_NAME = "fs_prefs"
private const val KEY_SELECTED_FOLDER_URI = "selected_folder_uri"

private fun newMessage(
    role: MessageRole,
    content: String,
    attachments: List<Attachment> = emptyList()
): ChatMessage {
    return ChatMessage(
        id = UUID.randomUUID().toString(),
        role = role,
        content = content,
        createdAt = System.currentTimeMillis().toString(),
        attachments = attachments
    )
}

private fun newEmptyConversation(): Conversation {
    return Conversation(
        id = UUID.randomUUID().toString(),
        title = "New chat",
        messages = emptyList(),
        updatedAt = System.currentTimeMillis().toString()
    )
}

private fun deriveTitle(text: String): String {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return "New chat"
    return if (trimmed.length > 40) trimmed.take(40) + "..." else trimmed
}

private fun summarizeForAi(results: List<FsOperationResult>): String {
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
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 150, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .padding(end = 4.dp)
                    .size(6.dp)
                    .background(Mint.copy(alpha = alpha), CircleShape)
            )
        }
    }
}

@Composable
fun ChatScreen(
    session: ChatSessionState,
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

    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var sidebarOpen by remember { mutableStateOf(false) }
    var editApiOpen by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var pendingAttachments by remember { mutableStateOf<List<Attachment>>(emptyList()) }
    var pendingFsRequest by remember { mutableStateOf<FsRequest?>(null) }
    val listState = rememberLazyListState()

    fun removeAttachment(attachment: Attachment) {
        pendingAttachments = pendingAttachments.filter { it != attachment }
    }

    fun refreshConversations() {
        conversations = if (searchQuery.isBlank()) conversationStore.listConversations()
        else conversationStore.searchConversations(searchQuery)
    }

    LaunchedEffect(Unit) {
        if (session.conversation == null) {
            session.conversation = newEmptyConversation()
        }
        refreshConversations()
    }
    LaunchedEffect(searchQuery) { refreshConversations() }

    fun handleDeleteConversation(id: String) {
        conversationStore.deleteConversation(id)
        if (session.conversation?.id == id) {
            session.conversation = newEmptyConversation()
        }
        refreshConversations()
    }

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
                        session.conversation, conversationStore
                    ) { session.conversation = it }
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
        val room = 20 - pendingAttachments.size
        pendingAttachments = pendingAttachments + newOnes.take(room.coerceAtLeast(0))
    }

    suspend fun runTurn(
        historyBeforeThisTurn: List<ChatMessage>,
        userText: String,
        attachments: List<Attachment>
    ): String {
        val turn = geminiClient.sendTurn(historyBeforeThisTurn, userText, attachments)

        if (turn.fsRequest == null) return turn.replyText

        val savedFolder = currentlyValidFolderUri(context, prefs)
        if (savedFolder == null) {
            pendingFsRequest = turn.fsRequest
            folderPickerLauncher.launch(null)
            return turn.replyText
        }

        val resolvedOps = turn.fsRequest.operations.map { op ->
            op.copy(rootPath = if (op.rootPath == "SELECTED_FOLDER") savedFolder else op.rootPath)
        }
        val results = resolvedOps.map { fsEngine.executeOperation(it) }
        val summary = summarizeForAi(results)

        val followUpHistory = historyBeforeThisTurn +
            newMessage(MessageRole.USER, userText, attachments) +
            newMessage(MessageRole.ASSISTANT, turn.replyText)
        val followUp = geminiClient.sendTurn(followUpHistory, summary, emptyList())
        return followUp.replyText
    }

    fun handleSend(text: String) {
        val convo = session.conversation ?: return
        if (sending) return

        val userMsg = newMessage(MessageRole.USER, text, pendingAttachments)
        val historyBefore = convo.messages
        val isFirst = convo.messages.isEmpty()

        var working = convo.copy(
            title = if (isFirst) deriveTitle(text) else convo.title,
            messages = convo.messages + userMsg,
            updatedAt = System.currentTimeMillis().toString()
        )
        session.conversation = working
        val attachmentsForSend = pendingAttachments
        pendingAttachments = emptyList()
        sending = true

        scope.launch {
            try {
                val assistantText = runTurn(historyBefore, text, attachmentsForSend)
                val assistantMsg = newMessage(MessageRole.ASSISTANT, assistantText)
                working = working.copy(
                    messages = working.messages + assistantMsg,
                    updatedAt = System.currentTimeMillis().toString()
                )
                session.conversation = working
                conversationStore.saveConversation(working)
                refreshConversations()
            } catch (e: Exception) {
                val errorMsg = newMessage(MessageRole.ASSISTANT, e.message ?: "Something went wrong.")
                working = working.copy(messages = working.messages + errorMsg)
                session.conversation = working
                conversationStore.saveConversation(working)
            } finally {
                sending = false
            }
        }
    }

    fun handleRetry(assistantMessageId: String) {
        val convo = session.conversation ?: return
        if (sending) return
        val messages = convo.messages
        val assistantIndex = messages.indexOfLast { it.id == assistantMessageId }
        if (assistantIndex <= 0) return
        val userMsg = messages[assistantIndex - 1]
        if (userMsg.role != MessageRole.USER) return

        val historyBefore = messages.take(assistantIndex - 1)
        sending = true

        scope.launch {
            try {
                val assistantText = runTurn(historyBefore, userMsg.content, userMsg.attachments)
                val newAssistantMsg = newMessage(MessageRole.ASSISTANT, assistantText)
                val updated = convo.copy(
                    messages = messages.take(assistantIndex) + newAssistantMsg,
                    updatedAt = System.currentTimeMillis().toString()
                )
                session.conversation = updated
                conversationStore.saveConversation(updated)
            } catch (e: Exception) {
                // Leave the old response in place on failure.
            } finally {
                sending = false
            }
        }
    }

    fun handleEditSave(userMessageId: String, newText: String) {
        val convo = session.conversation ?: return
        if (sending || newText.isBlank()) return
        val messages = convo.messages
        val userIndex = messages.indexOfLast { it.id == userMessageId }
        if (userIndex < 0) return
        val original = messages[userIndex]
        if (original.role != MessageRole.USER) return

        val historyBefore = messages.take(userIndex)
        val editedUserMsg = original.copy(content = newText)

        var working = convo.copy(
            messages = historyBefore + editedUserMsg,
            updatedAt = System.currentTimeMillis().toString()
        )
        session.conversation = working
        sending = true

        scope.launch {
            try {
                val assistantText = runTurn(historyBefore, newText, original.attachments)
                val assistantMsg = newMessage(MessageRole.ASSISTANT, assistantText)
                working = working.copy(
                    messages = working.messages + assistantMsg,
                    updatedAt = System.currentTimeMillis().toString()
                )
                session.conversation = working
                conversationStore.saveConversation(working)
                refreshConversations()
            } catch (e: Exception) {
                val errorMsg = newMessage(MessageRole.ASSISTANT, e.message ?: "Something went wrong.")
                working = working.copy(messages = working.messages + errorMsg)
                session.conversation = working
                conversationStore.saveConversation(working)
            } finally {
                sending = false
            }
        }
    }

    fun handleLike(messageId: String) {
        val convo = session.conversation ?: return
        val updated = convo.copy(
            messages = convo.messages.map {
                if (it.id == messageId) it.copy(liked = !it.liked, disliked = false) else it
            }
        )
        session.conversation = updated
        conversationStore.saveConversation(updated)
    }

    fun handleDislike(messageId: String) {
        val convo = session.conversation ?: return
        val updated = convo.copy(
            messages = convo.messages.map {
                if (it.id == messageId) it.copy(disliked = !it.disliked, liked = false) else it
            }
        )
        session.conversation = updated
        conversationStore.saveConversation(updated)
    }

    LaunchedEffect(session.conversation?.messages?.size, sending) {
        val size = session.conversation?.messages?.size ?: 0
        if (size > 0 || sending) listState.animateScrollToItem(listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1)
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

            val messages = session.conversation?.messages ?: emptyList()
            val latestUserId = messages.lastOrNull { it.role == MessageRole.USER }?.id
            val latestAiId = messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
            val showTyping = sending && (messages.isEmpty() || messages.last().role == MessageRole.USER)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        isLatestUserMessage = msg.id == latestUserId,
                        isLatestAiMessage = msg.id == latestAiId,
                        onLike = { handleLike(msg.id) },
                        onDislike = { handleDislike(msg.id) },
                        onRetry = { handleRetry(msg.id) },
                        onSaveEdit = { newText -> handleEditSave(msg.id, newText) }
                    )
                }
                if (showTyping) {
                    item { TypingDots() }
                }
            }

            MessageInputBar(
                enabled = !sending,
                onAttachClick = { attachLauncher.launch(arrayOf("text/plain", "text/markdown")) },
                pendingAttachments = pendingAttachments,
                onRemoveAttachment = { removeAttachment(it) },
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
                activeConversationId = session.conversation?.id,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSelectConversation = { id ->
                    session.conversation = conversationStore.getConversation(id)
                    sidebarOpen = false
                },
                onNewChat = {
                    session.conversation = newEmptyConversation()
                    refreshConversations()
                    sidebarOpen = false
                },
                onEditApi = { editApiOpen = true },
                onSelectFolder = {
                    pendingFsRequest = null
                    folderPickerLauncher.launch(null)
                    sidebarOpen = false
                },
                onDeleteConversation = { handleDeleteConversation(it) },
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