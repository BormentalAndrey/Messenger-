package com.kakdela.p2p.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object ModelDownloadManager {

    private const val MODEL_URL =
        "https://huggingface.co/TheBloke/Phi-3-mini-4k-instruct-GGUF/resolve/main/phi-3-mini-4k-instruct.Q4_K_M.gguf"

    private const val MODEL_NAME = "phi3.gguf"

    fun modelFile(context: Context): File =
        File(context.filesDir, "models/$MODEL_NAME")

    fun isInstalled(context: Context): Boolean =
        modelFile(context).exists()

    suspend fun download(
        context: Context,
        onProgress: (Int) -> Unit
    ) = withContext(Dispatchers.IO) {

        val file = modelFile(context)
        file.parentFile?.mkdirs()

        val client = OkHttpClient()
        val request = Request.Builder().url(MODEL_URL).build()
        val response = client.newCall(request).execute()

        val body = response.body ?: error("Empty body")
        val total = body.contentLength()

        var downloaded = 0L
        val buffer = ByteArray(8192)

        body.byteStream().use { input ->
            file.outputStream().use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    onProgress(((downloaded * 100) / total).toInt())
                }
            }
        }
    }
}
