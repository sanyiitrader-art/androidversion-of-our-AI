package com.fsstructurecreator.fs

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.fsstructurecreator.data.FsErrorCode
import com.fsstructurecreator.data.FsItemError
import com.fsstructurecreator.data.FsOperation
import com.fsstructurecreator.data.FsOperationResult
import com.fsstructurecreator.data.ItemKind

class FilesystemEngine(private val context: Context) {

    fun executeOperation(op: FsOperation): FsOperationResult {
        val created = mutableListOf<String>()
        val createdDirs = mutableListOf<String>()
        val errors = mutableListOf<FsItemError>()

        val rootUri = runCatching { Uri.parse(op.rootPath) }.getOrNull()
        val root = rootUri?.let { DocumentFile.fromTreeUri(context, it) }

        if (root == null || !root.isDirectory || !root.canWrite()) {
            errors.add(FsItemError(op.rootPath, ItemKind.DIRECTORY, FsErrorCode.ACCESS_DENIED))
            return FsOperationResult(op.rootPath, emptyList(), emptyList(), errors)
        }

        for (relDir in op.directories) {
            when (val result = createDirPath(root, relDir)) {
                is Result.Success -> createdDirs.add(relDir)
                is Result.Failure -> errors.add(FsItemError(relDir, ItemKind.DIRECTORY, result.code))
            }
        }

        for (relFile in op.files) {
            when (val result = createFilePath(root, relFile)) {
                is Result.Success -> created.add(relFile)
                is Result.Failure -> errors.add(FsItemError(relFile, ItemKind.FILE, result.code))
            }
        }

        return FsOperationResult(op.rootPath, createdDirs, created, errors)
    }

    private sealed class Result {
        object Success : Result()
        data class Failure(val code: FsErrorCode) : Result()
    }

    private fun splitAndValidate(relative: String): List<String>? {
        if (relative.isBlank()) return null
        val parts = relative.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        val invalidChars = charArrayOf('<', '>', ':', '"', '|', '?', '*')
        for (part in parts) {
            if (part == "..") return null
            if (part.any { it in invalidChars }) return null
            if (part.trimEnd('.').isEmpty()) return null
        }
        return parts
    }

    private fun createDirPath(root: DocumentFile, relative: String): Result {
        val parts = splitAndValidate(relative) ?: return Result.Failure(FsErrorCode.INVALID_FILENAME)
        var current = root
        for (part in parts) {
            val existing = current.findFile(part)
            current = when {
                existing != null && existing.isDirectory -> existing
                existing != null && !existing.isDirectory -> return Result.Failure(FsErrorCode.ALREADY_EXISTS)
                else -> current.createDirectory(part) ?: return Result.Failure(FsErrorCode.OTHER)
            }
        }
        return Result.Success
    }

    private fun createFilePath(root: DocumentFile, relative: String): Result {
        val parts = splitAndValidate(relative) ?: return Result.Failure(FsErrorCode.INVALID_FILENAME)
        val dirParts = parts.dropLast(1)
        val fileName = parts.last()

        var current = root
        for (part in dirParts) {
            val existing = current.findFile(part)
            current = when {
                existing != null && existing.isDirectory -> existing
                existing != null && !existing.isDirectory -> return Result.Failure(FsErrorCode.ALREADY_EXISTS)
                else -> current.createDirectory(part) ?: return Result.Failure(FsErrorCode.OTHER)
            }
        }

        if (current.findFile(fileName) != null) {
            return Result.Failure(FsErrorCode.ALREADY_EXISTS)
        }

        val mime = "application/octet-stream"
        val created = current.createFile(mime, fileName) ?: return Result.Failure(FsErrorCode.OTHER)

        return if (created.name == fileName) {
            Result.Success
        } else {
            // SAF appended an extension based on MIME guessing -- rename
            // back to the exact requested filename (spec section 13).
            if (created.renameTo(fileName)) Result.Success else Result.Success
        }
    }
}