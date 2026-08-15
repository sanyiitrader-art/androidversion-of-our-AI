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
        // SAF may append an extension based on MIME guessing -- rename
        // back to the exact requested name.
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

    /** Finds a unique root workspace folder name starting from "new folder",
     *  appending " (2)", " (3)", etc. as needed (spec sections 21-22). */
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