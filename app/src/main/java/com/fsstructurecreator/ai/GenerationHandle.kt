package com.fsstructurecreator.ai

import java.net.HttpURLConnection

/** Real, not-fake cancellation for an in-flight Gemini request.
 *  Kotlin coroutine cancellation alone does not interrupt a blocking
 *  HttpURLConnection read -- calling disconnect() on the live
 *  connection is what actually stops it promptly. */
class GenerationHandle {
    @Volatile var connection: HttpURLConnection? = null
    @Volatile var cancelled: Boolean = false

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }
}