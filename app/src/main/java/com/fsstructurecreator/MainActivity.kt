package com.fsstructurecreator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fsstructurecreator.editor.EditorScreen
import com.fsstructurecreator.editor.EditorSessionState
import com.fsstructurecreator.ui.CharcoalBg
import com.fsstructurecreator.ui.ChatScreen
import com.fsstructurecreator.ui.ChatSessionState
import com.fsstructurecreator.ui.FsStructureCreatorTheme

private enum class TopLevelScreen { AI, EDITOR }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf(TopLevelScreen.AI) }

    // Both created once here, at the app-session level, so they
    // survive switching between the AI and editor screens -- only
    // destroyed on a full app/process restart.
    val editorSession = remember { EditorSessionState() }
    val chatSession = remember { ChatSessionState() }

    FsStructureCreatorTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CharcoalBg
        ) {
            when (screen) {
                TopLevelScreen.AI -> ChatScreen(
                    session = chatSession,
                    onOpenEditor = { screen = TopLevelScreen.EDITOR },
                    onSwipeToEditor = { screen = TopLevelScreen.EDITOR }
                )
                TopLevelScreen.EDITOR -> EditorScreen(
                    session = editorSession,
                    onSwipeToAi = { screen = TopLevelScreen.AI }
                )
            }
        }
    }
}