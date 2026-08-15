package com.fsstructurecreator.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fsstructurecreator.ui.CharcoalBg
import com.fsstructurecreator.ui.TextPrimary
import com.fsstructurecreator.ui.TextSecondary

@Composable
fun TextEditorView(
    openFile: OpenFile?,
    onContentChange: (String) -> Unit
) {
    if (openFile == null) {
        Box(modifier = Modifier.fillMaxSize().background(CharcoalBg)) {
            Text(
                text = "No file open",
                color = TextSecondary,
                modifier = Modifier.padding(16.dp)
            )
        }
        return
    }

    val extension = openFile.name.substringAfterLast('.', "")

    val visualTransformation = remember(extension) {
        VisualTransformation { annotatedString ->
            if (isHighlightableExtension(extension)) {
                TransformedText(
                    highlightSyntax(annotatedString.text, extension),
                    OffsetMapping.Identity
                )
            } else {
                TransformedText(annotatedString, OffsetMapping.Identity)
            }
        }
    }

    OutlinedTextField(
        value = openFile.content,
        onValueChange = onContentChange,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = TextPrimary
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        ),
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    )
}