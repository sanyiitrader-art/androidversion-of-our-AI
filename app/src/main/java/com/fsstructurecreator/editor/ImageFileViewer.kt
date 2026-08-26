package com.fsstructurecreator.editor

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fsstructurecreator.ui.CharcoalBg
import com.fsstructurecreator.ui.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lightweight image viewer: decodes via BitmapFactory with automatic
 *  downsampling (never loads a full-resolution image into memory),
 *  reused for every supported extension. Formats the platform can't
 *  actually decode (RAW camera formats, PSD/AI/EPS/INDD/XCF, SVG,
 *  PDF, etc.) simply fail decode and fall back to an info card
 *  instead of garbled output -- no per-format special-casing, no new
 *  dependency needed. */
@Composable
fun ImageFileViewer(uri: String, name: String) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        bitmap = null
        failed = false
        val decoded = withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val targetSize = 1600
                var sample = 1
                while (bounds.outWidth / sample > targetSize || bounds.outHeight / sample > targetSize) {
                    sample *= 2
                }
                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
                resolver.openInputStream(Uri.parse(uri))?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            } catch (e: Exception) {
                null
            }
        }
        if (decoded != null) bitmap = decoded.asImageBitmap() else failed = true
    }

    Box(modifier = Modifier.fillMaxSize().background(CharcoalBg), contentAlignment = Alignment.Center) {
        val bmp = bitmap
        when {
            bmp != null -> Image(bitmap = bmp, contentDescription = name, modifier = Modifier.padding(16.dp))
            failed -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(ImageIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.padding(bottom = 8.dp))
                Text(name, color = TextSecondary)
                Text("Preview not available for this format", color = TextSecondary)
            }
            else -> Text("Loading...", color = TextSecondary)
        }
    }
}