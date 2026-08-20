package com.fsstructurecreator.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ConversationStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dir = File(context.filesDir, "conversations").apply { mkdirs() }

    /** Lists saved conversations. As a side effect, any conversation
     *  file found with zero messages is deleted -- a conversation is
     *  only ever meant to be persisted once it has at least one
     *  message (see saveConversation callers), so an empty file on
     *  disk is stale garbage (either from before this rule existed,
     *  or a rare edge case) and is cleaned up here rather than left
     *  to accumulate and clutter the sidebar. */
    fun listConversations(): List<ConversationSummary> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                val convo = runCatching { json.decodeFromString<Conversation>(f.readText()) }.getOrNull()
                when {
                    convo == null -> null
                    convo.messages.isEmpty() -> {
                        f.delete()
                        null
                    }
                    else -> ConversationSummary(convo.id, convo.title, convo.updatedAt)
                }
            }
            ?.sortedByDescending { it.updatedAt }
            ?: emptyList()
    }

    fun searchConversations(query: String): List<ConversationSummary> {
        if (query.isBlank()) return listConversations()
        val q = query.lowercase()
        return listConversations().filter { it.title.lowercase().contains(q) }
    }

    fun getConversation(id: String): Conversation {
        val file = File(dir, "$id.json")
        return json.decodeFromString(file.readText())
    }

    fun createConversation(): Conversation {
        val convo = Conversation(
            id = UUID.randomUUID().toString(),
            title = "New chat",
            messages = emptyList(),
            updatedAt = System.currentTimeMillis().toString()
        )
        saveConversation(convo)
        return convo
    }

    fun saveConversation(conversation: Conversation) {
        val file = File(dir, "${conversation.id}.json")
        file.writeText(json.encodeToString(conversation))
    }

    fun deleteConversation(id: String) {
        File(dir, "$id.json").delete()
    }
}