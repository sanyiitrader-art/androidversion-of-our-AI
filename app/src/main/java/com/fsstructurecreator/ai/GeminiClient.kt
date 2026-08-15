package com.fsstructurecreator.ai

import com.fsstructurecreator.data.ActionKind
import com.fsstructurecreator.data.AiTurnResult
import com.fsstructurecreator.data.Attachment
import com.fsstructurecreator.data.ChatMessage
import com.fsstructurecreator.data.FsOperation
import com.fsstructurecreator.data.FsRequest
import com.fsstructurecreator.data.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient(private val getApiKey: () -> String?) {

    // Switched from gemini-3.5-flash to gemini-3.5-flash-lite:
    // 1000 requests/day vs 120/day, well suited to this app's
    // structured, low-complexity interpretation task.
    private val endpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"

    private val json = Json { ignoreUnknownKeys = true }

    private val systemInstruction = """
        You are the interpreter for an Android filesystem structure creator app.

        You can ONLY create directories and empty files. You cannot write file
        contents, edit, delete, move, copy, rename existing items, or run any
        other operation.

        You must NEVER suggest, recommend, or create anything the user did not
        explicitly ask for. Suggestion is not authorization -- only an explicit
        instruction may produce a creation operation. Do not propose additional
        folders or files you think would be useful.

        Interpret natural language, ASCII/markdown trees, and attached .txt/.md
        files. Preserve exact filenames the user provides. When the user gives
        a file type and a bare name with no extension, choose the extension.
        When the user gives both an explicit filename AND a separate type,
        append the type as an additional extension rather than replacing the
        given name.

        Maintain conversation context: resolve "it", "that", "the other one",
        and similar references using prior turns in this conversation.

        The user has already selected a destination folder through the system
        picker. Use "SELECTED_FOLDER" as root_path to refer to that folder
        (the app resolves it internally) unless the user's own message clearly
        specifies a different destination.

        You must reply with ONLY a single JSON object and NOTHING else -- no
        markdown code fences, no commentary before or after it, matching
        exactly this shape:

        {
          "replyText": "<natural language reply to show the user>",
          "fsRequest": null | {
            "action": "create",
            "operations": [
              {
                "root_path": "SELECTED_FOLDER",
                "directories": ["<relative path>", ...],
                "files": ["<relative path>", ...]
              }
            ]
          }
        }

        Keep replyText brief and to the point -- do not add extra commentary.

        Set "fsRequest" to null for purely conversational turns. Only populate
        "fsRequest" when the user has explicitly instructed creation of
        specific directories/files. Never include file contents.
    """.trimIndent()

    suspend fun sendTurn(
        history: List<ChatMessage>,
        userMessage: String,
        attachments: List<Attachment>
    ): AiTurnResult = withContext(Dispatchers.IO) {
        val apiKey = getApiKey() ?: throw IllegalStateException(
            "No API key configured. Add your Gemini API key via Edit API."
        )

        val attachmentText = attachments.joinToString("\n\n") { a ->
            "--- Attached file: ${a.name} ---\n${a.content}"
        }
        val fullUserText = if (attachmentText.isNotBlank()) {
            "$userMessage\n\n$attachmentText"
        } else userMessage

        val body = buildJsonObject {
            putJsonObject("system_instruction") {
                putJsonArray("parts") { addJsonObject { put("text", systemInstruction) } }
            }
            putJsonArray("contents") {
                for (m in history) {
                    addJsonObject {
                        put("role", if (m.role == MessageRole.USER) "user" else "model")
                        putJsonArray("parts") { addJsonObject { put("text", m.content) } }
                    }
                }
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") { addJsonObject { put("text", fullUserText) } }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                putJsonObject("thinkingConfig") { put("thinkingLevel", "minimal") }
                put("maxOutputTokens", 4096)
            }
        }

        val url = URL(endpoint)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("x-goog-api-key", apiKey)
        conn.doOutput = true

        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val status = conn.responseCode
        if (status !in 200..299) {
            if (status == 400 || status == 401 || status == 403) {
                throw IllegalStateException("The saved API key was rejected. Please check it.")
            }
            throw IllegalStateException("Gemini request failed (status $status).")
        }

        val responseText = conn.inputStream.bufferedReader().use { it.readText() }
        val root = json.parseToJsonElement(responseText).jsonObjectOrNull()
        val parts = root
            ?.get("candidates")?.jsonArrayOrNull()
            ?.getOrNull(0)?.jsonObjectOrNull()
            ?.get("content")?.jsonObjectOrNull()
            ?.get("parts")?.jsonArrayOrNull()

        val rawText = parts
            ?.mapNotNull { it.jsonObjectOrNull() }
            ?.filter { it["thought"]?.jsonPrimitiveOrNull()?.content != "true" }
            ?.mapNotNull { it["text"]?.jsonPrimitiveOrNull()?.contentOrNull }
            ?.joinToString("")
            ?.trim()
            ?: throw IllegalStateException("Gemini returned an empty response.")

        if (rawText.isEmpty()) {
            throw IllegalStateException("Gemini returned an empty response.")
        }

        parseAiTurnResult(rawText)
    }

    private fun stripCodeFences(text: String): String {
        val trimmed = text.trim()
        val fence = Regex("^```(?:json)?\\s*([\\s\\S]*?)\\s*```$", RegexOption.IGNORE_CASE)
        val match = fence.find(trimmed)
        return match?.groupValues?.get(1) ?: text
    }

    private fun extractJsonObject(text: String): String {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return text
        return text.substring(start, end + 1)
    }

    private fun repairStrayBackslashes(text: String): String {
        val regex = Regex("\\\\(?![\"\\\\/bfnrt]|u[0-9a-fA-F]{4})")
        return regex.replace(text, "\\\\\\\\")
    }

    private fun robustJsonParse(rawText: String): JsonObject {
        val attempts = listOf(
            rawText,
            stripCodeFences(rawText),
            extractJsonObject(rawText),
            extractJsonObject(stripCodeFences(rawText)),
            repairStrayBackslashes(rawText),
            repairStrayBackslashes(extractJsonObject(stripCodeFences(rawText)))
        )
        for (attempt in attempts) {
            val parsed = runCatching { json.parseToJsonElement(attempt) }.getOrNull()
            val obj = parsed?.jsonObjectOrNull()
            if (obj != null) return obj
        }
        throw IllegalStateException("Gemini's response was not valid JSON.")
    }

    private fun parseAiTurnResult(rawText: String): AiTurnResult {
        val obj = robustJsonParse(rawText)

        val replyText = obj["replyText"]?.jsonPrimitiveOrNull()?.contentOrNull
            ?: throw IllegalStateException("Gemini's response was missing 'replyText'.")

        val fsRequestElement = obj["fsRequest"]
        if (fsRequestElement == null || fsRequestElement.toString() == "null") {
            return AiTurnResult(replyText, null)
        }

        val fsObj = fsRequestElement.jsonObjectOrNull()
            ?: throw IllegalStateException("Gemini's 'fsRequest' was malformed.")

        val action = fsObj["action"]?.jsonPrimitiveOrNull()?.contentOrNull
        if (action != "create") {
            throw IllegalStateException("Gemini's 'fsRequest.action' was not 'create'.")
        }

        val operationsArray = fsObj["operations"]?.jsonArrayOrNull()
        if (operationsArray == null || operationsArray.isEmpty()) {
            throw IllegalStateException("Gemini's 'fsRequest.operations' was missing or empty.")
        }

        val operations = operationsArray.map { opElement ->
            val opObj = opElement.jsonObjectOrNull()
                ?: throw IllegalStateException("Gemini produced a malformed operation.")
            val rootPath = opObj["root_path"]?.jsonPrimitiveOrNull()?.contentOrNull
            if (rootPath.isNullOrBlank()) {
                throw IllegalStateException("Gemini produced an operation with no root_path.")
            }
            val directories = opObj["directories"]?.jsonArrayOrNull()
                ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull } ?: emptyList()
            val files = opObj["files"]?.jsonArrayOrNull()
                ?.mapNotNull { it.jsonPrimitiveOrNull()?.contentOrNull } ?: emptyList()
            FsOperation(rootPath, directories, files)
        }

        return AiTurnResult(replyText, FsRequest(ActionKind.create, operations))
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject
    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray
    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive
}