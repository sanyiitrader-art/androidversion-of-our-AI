package com.fsstructurecreator.editor

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

private val IMAGE_EXTENSIONS = setOf(
    "jpg", "jpeg", "jpe", "jif", "jfif", "jfi", "png", "gif", "webp", "avif", "tiff", "tif",
    "bmp", "dib", "heif", "heic", "ico", "svg", "svgz", "ai", "eps", "pdf", "jp2", "j2k", "jpf",
    "jpx", "jpm", "mj2", "jxl", "bpg", "dng", "cr2", "cr3", "crw", "nef", "nrw", "arw", "srf",
    "sr2", "raf", "orf", "rw2", "pef", "psd", "pdn", "xcf", "ind", "indd", "indt", "pbm", "pgm",
    "ppm", "ras", "rgb", "tga"
)

fun isImageExtension(name: String): Boolean {
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in IMAGE_EXTENSIONS
}

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

            var suspicious = 0
            for (i in 0 until bytesRead) {
                val b = buffer[i].toInt() and 0xFF
                if (b == 0) return@use true
                val isPrintableOrWhitespace =
                    b in 0x20..0x7E || b == 0x09 || b == 0x0A || b == 0x0D || b >= 0x80
                if (!isPrintableOrWhitespace) suspicious++
            }
            suspicious.toDouble() / bytesRead > 0.10
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

    fun rename(parentUri: String, oldName: String, newName: String): RenameResult {
        return try {
            val parent = DocumentFile.fromTreeUri(context, Uri.parse(parentUri)) ?: return RenameResult.Failure
            if (newName != oldName && parent.findFile(newName) != null) return RenameResult.DuplicateName

            val target = parent.findFile(oldName) ?: return RenameResult.Failure
            val ok = target.renameTo(newName)
            if (!ok) return RenameResult.Failure

            val renamed = parent.findFile(newName) ?: target
            RenameResult.Success(renamed.uri.toString())
        } catch (e: Exception) {
            RenameResult.Failure
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

    sealed class RenameResult {
        data class Success(val newUri: String) : RenameResult()
        object DuplicateName : RenameResult()
        object Failure : RenameResult()
    }
}