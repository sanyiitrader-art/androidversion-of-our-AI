package com.fsstructurecreator.data

import kotlinx.serialization.Serializable

// Shared data schema for the whole app. Kotlin equivalent of the
// Windows project's types.ts + operations.rs combined into one file,
// since Android has no separate trusted/untrusted process boundary
// requiring a duplicate schema on both sides.

// ---- Filesystem operation schema ----

@Serializable
enum class ActionKind {
    create
}

@Serializable
data class FsOperation(
    // On Android this is a SAF document tree URI string (the user
    // picks a destination folder via the system picker), not a
    // Windows drive path -- see FilesystemEngine.kt for resolution.
    val rootPath: String,
    val directories: List<String> = emptyList(),
    val files: List<String> = emptyList()
)

@Serializable
data class FsRequest(
    val action: ActionKind,
    val operations: List<FsOperation>
) {
    /** Structural validation only -- mirrors FsRequest::validate() in
     *  the Windows operations.rs. Path-safety/SAF checks happen per
     *  item in FilesystemEngine.kt at execution time. */
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

// ---- Conversations ----

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
    // Native-chat feature additions. Defaults keep old saved
    // conversations (written before this feature existed) loading
    // fine with no migration step -- every prior message simply
    // deserializes as liked=false, disliked=false, attachments=[].
    val liked: Boolean = false,
    val disliked: Boolean = false,
    val attachments: List<Attachment> = emptyList()
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

// ---- Attachments ----

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

// ---- AI turn result ----

data class AiTurnResult(
    val replyText: String,
    val fsRequest: FsRequest?
)