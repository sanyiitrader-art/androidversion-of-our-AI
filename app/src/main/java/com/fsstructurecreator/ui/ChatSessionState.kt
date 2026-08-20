package com.fsstructurecreator.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fsstructurecreator.data.Conversation

/**
 * Holds "which conversation am I in" state outside of ChatScreen's own
 * composition, so switching to the editor and back doesn't destroy it
 * -- same pattern as EditorSessionState. Created once at the app-
 * session level (MainActivity) and passed down.
 */
class ChatSessionState {
    var conversation by mutableStateOf<Conversation?>(null)
}