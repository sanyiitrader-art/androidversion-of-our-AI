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

    fun isLikelyBinary(uri: String): Boolean {
        val inputStream = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return true
        return inputStream.use { stream ->
            val buffer = ByteArray(8192)
            val bytesRead = stream.read(buffer)
            if (bytesRead <= 0) return@use false

            var suspiciousCount = 0
            for (i in 0 until bytesRead) {
                val b = buffer[i].toInt() and 0xFF
                if (b == 0) return@use true
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
        return try {
            val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri)) ?: return CreateResult.Failure
            if (parent.findFile(name) != null) return CreateResult.DuplicateName

            val created = parent.createFile("application/octet-stream", name) ?: return CreateResult.Failure
            if (created.name != name) {
                created.renameTo(name)
            }
            CreateResult.Success(created.uri.toString())
        } catch (e: Exception) {
            CreateResult.Failure
        }
    }

    fun createFolder(parentUri: String, name: String): CreateResult {
        return try {
            val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri)) ?: return CreateResult.Failure
            if (parent.findFile(name) != null) return CreateResult.DuplicateName

            val created = parent.createDirectory(name) ?: return CreateResult.Failure
            CreateResult.Success(created.uri.toString())
        } catch (e: Exception) {
            CreateResult.Failure
        }
    }

    /** Renames a file or folder. Wrapped defensively: Android's
     *  DocumentFile.renameTo() can throw (some storage providers, or
     *  invalid target names, fail this way rather than just returning
     *  false) -- an uncaught throw here previously crashed the whole
     *  app instead of surfacing as a normal "couldn't rename" result. */
    fun rename(uri: String, newName: String): Boolean {
        return try {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(uri))
            doc?.renameTo(newName) ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun delete(uri: String): Boolean {
        return try {
            val doc = DocumentFile.fromSingleUri(context, Uri.parse(uri))
                ?: DocumentFile.fromTreeUri(context, Uri.parse(uri))
            doc?.delete() ?: false
        } catch (e: Exception) {
            false
        }
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