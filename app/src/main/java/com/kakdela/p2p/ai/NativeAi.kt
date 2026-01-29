package com.kakdela.p2p.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
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
            Log.e("LlamaBridge", "Failed to load library: ${e.message}")
        }
    }

    external fun init(modelPath: String)
    external fun isReady(): Boolean
    external fun prompt(text: String): String

    fun isLibAvailable(): Boolean = isLibLoaded
}

// --- Download Manager ---
object ModelDownloadManager {
    // Phi-3 Mini Instruct (Q4_K_M) - >1GB
    private const val MODEL_URL = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
    private const val MODEL_FILENAME = "phi3-mini-q4.gguf"

    fun getModelFile(context: Context): File {
        val modelsDir = File(context.filesDir, "models")
        if (!modelsDir.exists()) modelsDir.mkdirs()
        return File(modelsDir, MODEL_FILENAME)
    }

    fun isInstalled(context: Context): Boolean {
        val file = getModelFile(context)
        // Проверяем, что файл существует и его размер похож на правду (> 1GB)
        return file.exists() && file.length() > 1_000_000_000L
    }

    suspend fun download(context: Context, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val finalFile = getModelFile(context)
        val tempFile = File(finalFile.parent, "$MODEL_FILENAME.tmp")

        // Если остался мусор от прошлого раза - удаляем
        if (tempFile.exists()) tempFile.delete()

        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val request = Request.Builder().url(MODEL_URL).build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")

            val body = response.body ?: throw IOException("Empty body")
            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            val buffer = ByteArray(8192) // 8KB buffer

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

            // --- КРИТИЧЕСКОЕ ИСПРАВЛЕНИЕ: Переименование ---
            
            // 1. Если целевой файл уже есть (например, битый), удаляем его
            if (finalFile.exists()) {
                if (!finalFile.delete()) {
                    throw IOException("Cannot delete existing corrupted model file.")
                }
            }

            // 2. Пытаемся сделать атомарный rename
            val renameSuccess = tempFile.renameTo(finalFile)
            
            // 3. Если rename не сработал (бывает на некоторых Android), делаем копирование
            if (!renameSuccess) {
                Log.w("ModelDownload", "renameTo failed, trying manual copy...")
                copyAndCleanup(tempFile, finalFile)
            }

        } catch (e: Exception) {
            tempFile.delete() // Чистим за собой при ошибке
            throw e
        }
    }

    private fun copyAndCleanup(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
        if (dest.exists() && dest.length() > 0) {
            source.delete()
        } else {
            throw IOException("Failed to copy temp file to final destination")
        }
    }
}
