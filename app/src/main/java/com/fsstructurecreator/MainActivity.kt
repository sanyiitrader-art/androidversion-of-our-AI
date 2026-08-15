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
import com.fsstructurecreator.ui.CharcoalBg
import com.fsstructurecreator.ui.ChatScreen
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

    FsStructureCreatorTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CharcoalBg
        ) {
            when (screen) {
                TopLevelScreen.AI -> ChatScreen(
                    onOpenEditor = { screen = TopLevelScreen.EDITOR },
                    onSwipeToEditor = { screen = TopLevelScreen.EDITOR }
                )
                TopLevelScreen.EDITOR -> EditorScreen(
                    onSwipeToAi = { screen = TopLevelScreen.AI }
                )
            }
        }
    }
}