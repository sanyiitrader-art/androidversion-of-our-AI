package com.fsstructurecreator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fsstructurecreator.ui.CharcoalBg
import com.fsstructurecreator.ui.ChatScreen
import com.fsstructurecreator.ui.FsStructureCreatorTheme

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
    FsStructureCreatorTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = CharcoalBg
        ) {
            ChatScreen()
        }
    }
}