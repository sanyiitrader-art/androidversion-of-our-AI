package com.fsstructurecreator.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class EditorSessionState {
    var workspaceRoot by mutableStateOf<String?>(null)
    var tree by mutableStateOf<WorkspaceNode?>(null)
    var openFile by mutableStateOf<OpenFile?>(null)
    var openImageUri by mutableStateOf<String?>(null)
    var navHistory by mutableStateOf(NavigationHistory())
    var autoSave by mutableStateOf(false)
    var selectedUri by mutableStateOf<String?>(null)
    var unsavedEdits by mutableStateOf<Map<String, String>>(emptyMap())
    var unsupportedFileMessage by mutableStateOf<String?>(null)
}