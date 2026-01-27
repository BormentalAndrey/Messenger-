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
/**
 * Объект для взаимодействия с нативным кодом llama.cpp через JNI.
 */
object LlamaBridge {
    private var isLoaded = false

    init {
        try {
            // Загрузка библиотеки libllama.so
            System.loadLibrary("llama")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("Native library 'llama' not found. Ensure CMake and NDK are configured: ${e.message}")
        }
    }

    /**
     * Инициализирует модель по указанному пути.
     */
    external fun init(modelPath: String)

    /**
     * Отправляет запрос модели и возвращает текстовый ответ.
     */
    external fun prompt(text: String): String

    fun isReady(): Boolean = isLoaded
}

// --- Download Manager ---
/**
 * Управляет загрузкой и проверкой целостности файла весов ИИ.
 */
object ModelDownloadManager {
    // Прямая стабильная ссылка от Microsoft (Phi-3-mini-4k-instruct-q4.gguf)
    private const val MODEL_URL = 
        "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
    
    private const val MODEL_NAME = "phi3-mini-q4.gguf"

    /**
     * Возвращает путь к файлу модели в защищенном внутреннем хранилище приложения.
     */
    fun modelFile(context: Context): File =
        File(context.filesDir, "models/$MODEL_NAME")

    /**
     * Проверяет, установлена ли модель и не является ли файл поврежденным (проверка размера).
     */
    fun isInstalled(context: Context): Boolean {
        val file = modelFile(context)
        // Модель Phi-3 Q4 весит примерно 2.2 ГБ. Проверка на > 1 ГБ отсекает пустые/битые файлы.
        return file.exists() && file.length() > 1_000_000_000
    }

    /**
     * Скачивает модель с поддержкой редиректов и обработкой заголовков для предотвращения ошибки 401.
     */
    suspend fun download(
        context: Context,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val file = modelFile(context)
        file.parentFile?.mkdirs()

        // Временный файл для безопасной загрузки (предотвращает использование недокачанного файла)
        val tempFile = File(file.parentFile, "$MODEL_NAME.tmp")

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true) // Критично для Hugging Face LFS
            .build()

        val request = Request.Builder()
            .url(MODEL_URL)
            // Имитация браузера для обхода блокировок автоматических загрузок (ошибка 401/403)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Failed to download model: HTTP ${response.code} ${response.message}")
            }

            val body = response.body ?: throw IOException("Empty response body from server")
            val total = body.contentLength()

            var downloaded = 0L
            val buffer = ByteArray(16384) // Увеличенный буфер для быстрой записи тяжелых файлов
            var lastProgress = -1

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        
                        output.write(buffer, 0, read)
                        downloaded += read
                        
                        // Частота обновления прогресса ограничена для экономии ресурсов CPU
                        if (total > 0) {
                            val currentProgress = ((downloaded * 100) / total).toInt()
                            if (currentProgress > lastProgress) {
                                lastProgress = currentProgress
                                withContext(Dispatchers.Main) {
                                    onProgress(currentProgress)
                                }
                            }
                        }
                    }
                }
            }
            
            // Замена старого файла новым только после успешного завершения записи
            if (tempFile.exists() && tempFile.length() > 1_000_000_000) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    throw IOException("Failed to finalize model file (rename error)")
                }
            } else {
                throw IOException("Downloaded file is incomplete or corrupted")
            }
            
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }
}
