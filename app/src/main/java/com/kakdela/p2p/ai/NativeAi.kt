package com.kakdela.p2p.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

// --- Llama Bridge ---
object LlamaBridge {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("llama")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            // Библиотека не собрана или архитектура не совпадает
            e.printStackTrace()
        }
    }

    external fun init(modelPath: String)
    external fun prompt(text: String): String

    fun isReady(): Boolean = isLoaded
}

// --- Download Manager ---
object ModelDownloadManager {
    // Ссылка на Q4_K_M версию (оптимальный баланс для Android)
    private const val MODEL_URL = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
    private const val MODEL_NAME = "phi3-mini-q4.gguf"

    fun modelFile(context: Context): File = File(context.filesDir, "models/$MODEL_NAME")

    fun isInstalled(context: Context): Boolean {
        val file = modelFile(context)
        return file.exists() && file.length() > 1_000_000_000 // > 1GB
    }

    suspend fun download(context: Context, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val file = modelFile(context)
        file.parentFile?.mkdirs()
        val tempFile = File(file.parent, "$MODEL_NAME.tmp")

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS) // Долгий таймаут для больших файлов
            .followRedirects(true)
            .build()

        val request = Request.Builder().url(MODEL_URL).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Server code: ${response.code}")

            val body = response.body ?: throw IOException("Empty body")
            val total = body.contentLength()
            var downloaded = 0L
            val buffer = ByteArray(8192)

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) onProgress(((downloaded * 100) / total).toInt())
                    }
                }
            }

            if (tempFile.renameTo(file)) {
                // Успех
            } else {
                throw IOException("Rename failed")
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}
