package com.kakdela.p2p.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

// --- Llama Bridge ---
/**
 * Объект для взаимодействия с нативным кодом llama.cpp через JNI.
 * Обеспечивает безопасную загрузку библиотеки и вызов методов ИИ.
 */
object LlamaBridge {
    private var isLoaded = false

    init {
        try {
            // Имя библиотеки должно соответствовать вашему CMakeLists.txt (обычно "llama")
            System.loadLibrary("llama")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("CRITICAL: Native library 'llama' not found: ${e.message}")
        } catch (e: Exception) {
            System.err.println("CRITICAL: Failed to load native library: ${e.message}")
        }
    }

    /**
     * Инициализирует модель по указанному пути. 
     * ВНИМАНИЕ: Вызывайте только в Dispatchers.IO, так как это тяжелая операция.
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
 * Управляет загрузкой файла весов (GGUF) и проверкой его целостности.
 */
object ModelDownloadManager {
    // Используем стабильную официальную ссылку от Microsoft
    private const val MODEL_URL = 
        "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf"
    
    private const val MODEL_NAME = "phi3-mini-q4.gguf"

    /**
     * Возвращает путь к файлу модели. Используем подпапку "models" во внутреннем хранилище.
     */
    fun modelFile(context: Context): File =
        File(context.filesDir, "models/$MODEL_NAME")

    /**
     * Проверяет наличие и примерную целостность файла.
     */
    fun isInstalled(context: Context): Boolean {
        val file = modelFile(context)
        // Модель Microsoft Phi-3 Q4 весит ~2.3 ГБ. 1 ГБ — порог отсечения недокачанных файлов.
        return file.exists() && file.length() > 1_000_000_000
    }

    /**
     * Скачивает модель. Реализована защита от 401 ошибки и поддержка редиректов.
     */
    suspend fun download(
        context: Context,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val file = modelFile(context)
        
        // Создаем директорию, если её нет
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }

        // Временный файл, чтобы не заблокировать инициализацию битым файлом при обрыве связи
        val tempFile = File(parent, "$MODEL_NAME.tmp")

        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true) // Обязательно для Hugging Face LFS
            .build()

        val request = Request.Builder()
            .url(MODEL_URL)
            // Имитация браузера позволяет избежать ошибки 401/403 на некоторых CDN
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Ошибка сервера: ${response.code} ${response.message}")
            }

            val body = response.body ?: throw IOException("Тело ответа пустое")
            val totalBytes = body.contentLength()

            var bytesDownloaded = 0L
            val buffer = ByteArray(16384) // 16KB буфер для оптимальной скорости на Android
            var lastPublishedProgress = -1

            body.byteStream().use { input ->
                tempFile.outputStream().use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead

                        if (totalBytes > 0) {
                            val progress = ((bytesDownloaded * 100) / totalBytes).toInt()
                            // Обновляем UI только при изменении процента
                            if (progress != lastPublishedProgress) {
                                lastPublishedProgress = progress
                                withContext(Dispatchers.Main) {
                                    onProgress(progress)
                                }
                            }
                        }
                    }
                }
            }

            // Финализация: заменяем старый файл (если был) на новый
            if (tempFile.exists() && tempFile.length() > 1_000_000_000) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    throw IOException("Не удалось переименовать временный файл модели")
                }
            } else {
                throw IOException("Загруженный файл поврежден или имеет неверный размер")
            }

        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            throw e
        }
    }
}
