package com.kakdela.p2p.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

// --- Llama Bridge ---
object LlamaBridge {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("llama")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            // Обработка отсутствия .so библиотеки (важно, чтобы приложение не падало сразу)
            System.err.println("Native library 'llama' not found: ${e.message}")
        }
    }

    external fun init(modelPath: String)
    external fun prompt(text: String): String

    fun isReady(): Boolean = isLoaded
}

// --- Download Manager ---
object ModelDownloadManager {
    // Ссылка на Phi-3 Mini GGUF (4-bit quantization)
    private const val MODEL_URL =
        "https://huggingface.co/TheBloke/Phi-3-mini-4k-instruct-GGUF/resolve/main/phi-3-mini-4k-instruct.Q4_K_M.gguf"
    
    private const val MODEL_NAME = "phi3-mini.gguf"

    fun modelFile(context: Context): File =
        File(context.filesDir, "models/$MODEL_NAME")

    fun isInstalled(context: Context): Boolean {
        val file = modelFile(context)
        // Проверяем не только существование, но и размер (грубая защита от битых файлов)
        return file.exists() && file.length() > 100_000_000 // > 100MB
    }

    suspend fun download(
        context: Context,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val file = modelFile(context)
        file.parentFile?.mkdirs()

        // Временный файл для безопасной загрузки
        val tempFile = File(file.parentFile, "$MODEL_NAME.tmp")

        val client = OkHttpClient()
        val request = Request.Builder().url(MODEL_URL).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Failed to download model: $response")

            val body = response.body ?: throw IOException("Empty body")
            val total = body.contentLength()

            var downloaded = 0L
            val buffer = ByteArray(8192)
            var lastProgress = 0

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        
                        // Обновляем прогресс только если он изменился, чтобы не спамить UI
                        val currentProgress = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        if (currentProgress > lastProgress) {
                            lastProgress = currentProgress
                            onProgress(currentProgress)
                        }
                    }
                }
            }
            
            // Переименовываем успешный файл
            if (file.exists()) file.delete()
            tempFile.renameTo(file)
            
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}
