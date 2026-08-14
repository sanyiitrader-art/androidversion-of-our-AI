package com.fsstructurecreator

import android.app.Application

// Minimal Application subclass, referenced by AndroidManifest.xml.
// No custom initialization needed -- all stores (ConversationStore,
// SettingsStore) are created lazily inside ChatScreen.kt via
// LocalContext, so this file exists only because the manifest
// requires a named Application class to exist.
class FsStructureCreatorApp : Application()