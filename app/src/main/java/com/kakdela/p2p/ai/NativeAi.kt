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
    private var isLibLoaded = false

    init {
        try {
            System.loadLibrary("llama")
            isLibLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
            // Если библиотека не загрузилась, флаг останется false
        }
    }

    external fun init(modelPath: String)
    external fun isReady(): Boolean
    external fun prompt(text: String): String

    fun isLibAvailable(): Boolean = isLibLoaded
}

// --- Download Manager ---
object ModelDownloadManager {
    // Используем Phi-3 Mini Instruct (формат GGUF Q4_K_M) - легкая и умная
    private const val MODEL_URL = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
    private const val MODEL_FILENAME = "phi3-mini-q4.gguf"

    // Централизованный метод получения пути к файлу
    fun getModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        return File(modelsDir, MODEL_FILENAME)
    }

    // Проверка целостности файла (размер > 1ГБ)
    fun isInstalled(context: Context): Boolean {
        val file = getModelFile(context)
        return file.exists() && file.length() > 1_000_000_000L
    }

    suspend fun download(context: Context, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val finalFile = getModelFile(context)
        val tempFile = File(finalFile.parent, "$MODEL_FILENAME.tmp")

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS) // Увеличенный таймаут
            .followRedirects(true)
            .build()

        val request = Request.Builder().url(MODEL_URL).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Failed to download: ${response.code}")

            val body = response.body ?: throw IOException("Empty body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            onProgress(((downloadedBytes * 100) / totalBytes).toInt())
                        }
                    }
                }
            }

            // Переименование после успешной загрузки (атомарная операция)
            if (tempFile.renameTo(finalFile)) {
                // Успех
            } else {
                throw IOException("Failed to rename temp file to model file")
            }
        } catch (e: Exception) {
            tempFile.delete() // Удаляем мусор при ошибке
            throw e
        }
    }
}
