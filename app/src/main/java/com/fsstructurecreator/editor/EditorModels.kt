package com.fsstructurecreator.editor

data class WorkspaceNode(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val parentUri: String?,
    val depth: Int,
    val children: List<WorkspaceNode> = emptyList(),
    val isExpanded: Boolean = false
)

data class OpenFile(
    val uri: String,
    val name: String,
    val content: String,
    val isDirty: Boolean = false
)

data class NavigationHistory(
    val back: List<String> = emptyList(),
    val current: String? = null,
    val forward: List<String> = emptyList()
) {
    fun navigateTo(uri: String): NavigationHistory {
        val newBack = if (current != null) back + current else back
        return NavigationHistory(back = newBack, current = uri, forward = emptyList())
    }

    fun goBack(): NavigationHistory {
        if (back.isEmpty()) return this
        val newCurrent = back.last()
        val newForward = if (current != null) listOf(current) + forward else forward
        return NavigationHistory(back = back.dropLast(1), current = newCurrent, forward = newForward)
    }

    fun goForward(): NavigationHistory {
        if (forward.isEmpty()) return this
        val newCurrent = forward.first()
        val newBack = if (current != null) back + current else back
        return NavigationHistory(back = newBack, current = newCurrent, forward = forward.drop(1))
    }

    val canGoBack: Boolean get() = back.isNotEmpty()
    val canGoForward: Boolean get() = forward.isNotEmpty()
}

data class FileSearchResult(
    val uri: String,
    val name: String,
    val pathHint: String
)

data class TextSearchResult(
    val lineNumber: Int,
    val lineText: String,
    val matchStart: Int,
    val matchEnd: Int
)

enum class WorkspaceSearchMode {
    FILE_NAME,
    TEXT_IN_FILE
}

enum class CreationErrorState {
    NONE,
    DUPLICATE_NAME
}