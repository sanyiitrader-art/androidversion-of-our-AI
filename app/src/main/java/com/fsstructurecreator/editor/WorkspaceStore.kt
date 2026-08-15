package com.fsstructurecreator.editor

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

class WorkspaceStore(private val context: Context) {

    fun loadTree(rootUri: String): WorkspaceNode? {
        val uri = Uri.parse(rootUri)
        val root = DocumentFile.fromTreeUri(context, uri) ?: return null
        return buildNode(root, parentUri = null, depth = 0)
    }

    private fun buildNode(doc: DocumentFile, parentUri: String?, depth: Int): WorkspaceNode {
        val uriString = doc.uri.toString()
        val children = if (doc.isDirectory) {
            doc.listFiles()
                .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name ?: "" })
                .map { buildNode(it, uriString, depth + 1) }
        } else emptyList()

        return WorkspaceNode(
            uri = uriString,
            name = doc.name ?: "unnamed",
            isDirectory = doc.isDirectory,
            parentUri = parentUri,
            depth = depth,
            children = children,
            isExpanded = false
        )
    }

    /** Sniffs the first few KB of a file's actual bytes to decide
     *  whether it's safe to load as text. Deliberately NOT based on
     *  file extension (spec section 30 -- no hardcoded file-type
     *  list): a null byte, or a high proportion of non-printable
     *  control bytes, reliably indicates binary content regardless of
     *  what the file is named. */
    fun isLikelyBinary(uri: String): Boolean {
        val inputStream = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return true
        return inputStream.use { stream ->
            val buffer = ByteArray(8192)
            val bytesRead = stream.read(buffer)
            if (bytesRead <= 0) return@use false

            var suspiciousCount = 0
            for (i in 0 until bytesRead) {
                val b = buffer[i].toInt() and 0xFF
                if (b == 0) return@use true // null byte -- definitively binary
                val isPrintableOrWhitespace =
                    b in 0x20..0x7E || b == 0x09 || b == 0x0A || b == 0x0D || b >= 0x80
                if (!isPrintableOrWhitespace) suspiciousCount++
            }
            suspiciousCount.toDouble() / bytesRead > 0.10
        }
    }

    fun readFile(uri: String): String {
        val inputStream = context.contentResolver.openInputStream(Uri.parse(uri))
        return inputStream?.bufferedReader()?.use { it.readText() } ?: ""
    }

    fun writeFile(uri: String, content: String) {
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt")?.use {
            it.write(content.toByteArray())
        }
    }

    fun createFile(parentUri: String, name: String): CreateResult {
        val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri)) ?: return CreateResult.Failure
        if (parent.findFile(name) != null) return CreateResult.DuplicateName

        val created = parent.createFile("application/octet-stream", name) ?: return CreateResult.Failure
        if (created.name != name) {
            created.renameTo(name)
        }
        return CreateResult.Success(created.uri.toString())
    }

    fun createFolder(parentUri: String, name: String): CreateResult {
        val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri)) ?: return CreateResult.Failure
        if (parent.findFile(name) != null) return CreateResult.DuplicateName

        val created = parent.createDirectory(name) ?: return CreateResult.Failure
        return CreateResult.Success(created.uri.toString())
    }

    fun rename(uri: String, newName: String): Boolean {
        val doc = DocumentFile.fromSingleUri(context, Uri.parse(uri)) ?: return false
        return doc.renameTo(newName)
    }

    fun uniqueWorkspaceFolderName(parentUri: String, baseName: String = "new folder"): String {
        val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri)) ?: return baseName
        if (parent.findFile(baseName) == null) return baseName
        var counter = 2
        while (parent.findFile("$baseName ($counter)") != null) {
            counter++
        }
        return "$baseName ($counter)"
    }

    sealed class CreateResult {
        data class Success(val uri: String) : CreateResult()
        object DuplicateName : CreateResult()
        object Failure : CreateResult()
    }
}