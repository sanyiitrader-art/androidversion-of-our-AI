package com.fsstructurecreator.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Holds the editor's "which workspace/file am I in" state outside of
 * EditorScreen's own composition, so switching to the AI screen and
 * back doesn't destroy it. This must be created once at the
 * MainActivity level (which stays mounted for the whole app session)
 * and passed down into EditorScreen -- per spec, this state should
 * only ever be cleared by a full app restart, never by navigating
 * between the AI and editor screens.
 */
class EditorSessionState {
    var workspaceRoot by mutableStateOf<String?>(null)
    var tree by mutableStateOf<WorkspaceNode?>(null)
    var openFile by mutableStateOf<OpenFile?>(null)
    var navHistory by mutableStateOf(NavigationHistory())
    var autoSave by mutableStateOf(false)
    var selectedUri by mutableStateOf<String?>(null)
    var unsavedEdits by mutableStateOf<Map<String, String>>(emptyMap())
    var unsupportedFileMessage by mutableStateOf<String?>(null)
}