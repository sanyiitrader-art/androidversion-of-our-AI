package com.fsstructurecreator.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class ConversationStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val dir = File(context.filesDir, "conversations").apply { mkdirs() }

    fun listConversations(): List<ConversationSummary> {
        return dir.listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f ->
                runCatching { json.decodeFromString<Conversation>(f.readText()) }
                    .getOrNull()
                    ?.let { ConversationSummary(it.id, it.title, it.updatedAt) }
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