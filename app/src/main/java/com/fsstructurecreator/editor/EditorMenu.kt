package com.fsstructurecreator.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fsstructurecreator.ui.CharcoalElevated
import com.fsstructurecreator.ui.Mint
import com.fsstructurecreator.ui.TextPrimary

@Composable
fun EditorMenu(
    visible: Boolean,
    autoSave: Boolean,
    onAutoSaveToggle: (Boolean) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onSave: () -> Unit,
    onSaveAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        ) {
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .background(CharcoalElevated, RoundedCornerShape(10.dp))
                    .padding(vertical = 8.dp)
                    .padding(top = 56.dp, start = 12.dp)
            ) {
                MenuItem("New File", onNewFile)
                MenuItem("New Folder", onNewFolder)
                MenuItem("Open File", onOpenFile)
                MenuItem("Open Folder", onOpenFolder)
                MenuItem("Save", onSave)
                MenuItem("Save All", onSaveAll)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = autoSave,
                        onCheckedChange = onAutoSaveToggle,
                        colors = CheckboxDefaults.colors(checkedColor = Mint)
                    )
                    Text("Auto Save", color = TextPrimary)
                }
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    )
}