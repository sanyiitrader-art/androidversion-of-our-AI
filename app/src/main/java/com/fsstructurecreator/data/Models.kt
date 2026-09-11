package com.fsstructurecreator.data

import kotlinx.serialization.Serializable

@Serializable
enum class ActionKind {
    create
}

@Serializable
data class FsOperation(
    val rootPath: String,
    val directories: List<String> = emptyList(),
    val files: List<String> = emptyList()
)

@Serializable
data class FsRequest(
    val action: ActionKind,
    val operations: List<FsOperation>
) {
    fun validate(): String? {
        if (operations.isEmpty()) {
            return "Request contains no operations."
        }
        for (op in operations) {
            if (op.rootPath.isBlank()) {
                return "An operation is missing a rootPath."
            }
            if (op.directories.isEmpty() && op.files.isEmpty()) {
                return "Operation for '${op.rootPath}' has no directories or files."
            }
        }
        return null
    }
}

enum class ItemKind {
    DIRECTORY,
    FILE
}

enum class FsErrorCode {
    ALREADY_EXISTS,
    PATH_NOT_FOUND,
    INVALID_PATH,
    ACCESS_DENIED,
    INVALID_FILENAME,
    INVALID_CHARACTERS,
    OTHER
}

data class FsItemError(
    val path: String,
    val itemKind: ItemKind,
    val error: FsErrorCode
)

data class FsOperationResult(
    val rootPath: String,
    val createdDirectories: List<String>,
    val createdFiles: List<String>,
    val errors: List<FsItemError>
)

enum class MessageRole {
    USER,
    ASSISTANT
}

@Serializable
data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val createdAt: String,
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    // Client-generated status messages, never real AI output. Both
    // default to false so old saved conversations still deserialize
    // fine. Messages tagged either true are excluded from the history
    // sent to the model (see ChatScreen.runTurn) -- this is the fix
    // for the bug where a failed/stopped exchange got fed back to the
    // AI as real context and confused the next unrelated prompt.
    val isError: Boolean = false,
    val isStopped: Boolean = false
)

@Serializable
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage>,
    val updatedAt: String
)

data class ConversationSummary(
    val id: String,
    val title: String,
    val updatedAt: String
)

enum class AttachmentKind {
    TXT,
    MD
}

@Serializable
data class Attachment(
    val name: String,
    val kind: AttachmentKind,
    val content: String
)

data class AiTurnResult(
    val replyText: String,
    val fsRequest: FsRequest?
)