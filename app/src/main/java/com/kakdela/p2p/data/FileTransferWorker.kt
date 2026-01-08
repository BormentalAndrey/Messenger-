package com.kakdela.p2p.data

import android.content.Context
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

/**
 * P2P File Transfer (UDP signaling + chunking)
 *
 * ✔ chunk-based
 * ✔ bidirectional
 * ✔ без TCP сервера
 * ✔ безопасно для параллельных загрузок
 */
class FileTransferWorker(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {

    companion object {
        private const val CHUNK_SIZE = 16 * 1024 // 16 KB
        private const val TAG = "P2P_FILE"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /* ===================== SEEDING ===================== */

    /** fileId -> File */
    private val seedingFiles = ConcurrentHashMap<String, File>()

    /* ===================== DOWNLOADING ===================== */

    /**
     * fileId -> DownloadSession
     */
    private val activeDownloads = ConcurrentHashMap<String, DownloadSession>()

    /* ===================== LISTENER ===================== */

    private val listener: (String, String, String) -> Unit = { type, data, fromIp ->
        when (type) {
            "FILE_CHUNK_REQ" -> handleChunkRequest(data, fromIp)
            "FILE_CHUNK_DATA" -> handleChunkData(data)
        }
    }

    init {
        identityRepo.addListener(listener)
    }

    fun release() {
        identityRepo.removeListener(listener)
        scope.cancel()
    }

    /* ===================================================== */
    /* ===================== SEEDING ======================= */
    /* ===================================================== */

    fun addFileToSeeding(file: File): String {
        val fileId = UUID.randomUUID().toString()
        seedingFiles[fileId] = file
        Log.d(TAG, "Seeding file ${file.name} id=$fileId")
        return fileId
    }

    private fun handleChunkRequest(jsonData: String, targetIp: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonData)
                val fileId = json.getString("file_id")
                val chunkIndex = json.getInt("chunk_index")

                val file = seedingFiles[fileId] ?: return@launch

                RandomAccessFile(file, "r").use { raf ->
                    val offset = chunkIndex.toLong() * CHUNK_SIZE
                    if (offset >= file.length()) return@use

                    raf.seek(offset)

                    val buffer = ByteArray(CHUNK_SIZE)
                    val read = raf.read(buffer)
                    if (read <= 0) return@use

                    val payload = if (read == CHUNK_SIZE) buffer else buffer.copyOf(read)

                    val response = JSONObject().apply {
                        put("file_id", fileId)
                        put("chunk_index", chunkIndex)
                        put(
                            "data",
                            Base64.encodeToString(payload, Base64.NO_WRAP)
                        )
                    }

                    identityRepo.sendSignaling(
                        targetIp,
                        "FILE_CHUNK_DATA",
                        response.toString()
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Chunk request error", e)
            }
        }
    }

    /* ===================================================== */
    /* ===================== DOWNLOAD ====================== */
    /* ===================================================== */

    suspend fun downloadFileP2P(
        targetIp: String,
        fileId: String,
        fileName: String,
        totalChunks: Int
    ): File = withContext(Dispatchers.IO) {

        val outputFile = File(context.getExternalFilesDir(null), fileName)
        val session = DownloadSession(
            fileId = fileId,
            file = outputFile,
            totalChunks = totalChunks
        )

        activeDownloads[fileId] = session

        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.setLength(totalChunks.toLong() * CHUNK_SIZE)
        }

        // Запрашиваем чанки
        for (i in 0 until totalChunks) {
            val req = JSONObject().apply {
                put("file_id", fileId)
                put("chunk_index", i)
            }

            identityRepo.sendSignaling(
                targetIp,
                "FILE_CHUNK_REQ",
                req.toString()
            )

            delay(40) // не флудим сеть
        }

        // Ждём, пока придут ВСЕ чанки
        session.await()

        activeDownloads.remove(fileId)

        Log.d(TAG, "Download complete: ${outputFile.absolutePath}")
        return@withContext outputFile
    }

    private fun handleChunkData(jsonData: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonData)
                val fileId = json.getString("file_id")
                val chunkIndex = json.getInt("chunk_index")
                val dataB64 = json.getString("data")

                val session = activeDownloads[fileId] ?: return@launch
                val bytes = Base64.decode(dataB64, Base64.NO_WRAP)

                RandomAccessFile(session.file, "rw").use { raf ->
                    raf.seek(chunkIndex.toLong() * CHUNK_SIZE)
                    raf.write(bytes)
                }

                session.markChunkReceived(chunkIndex)
            } catch (e: Exception) {
                Log.e(TAG, "Chunk data error", e)
            }
        }
    }

    /* ===================================================== */
    /* ===================== SESSION ======================= */
    /* ===================================================== */

    private class DownloadSession(
        val fileId: String,
        val file: File,
        val totalChunks: Int
    ) {
        private val received = BooleanArray(totalChunks)
        private val latch = CountDownLatch(totalChunks)

        fun markChunkReceived(index: Int) {
            synchronized(received) {
                if (!received[index]) {
                    received[index] = true
                    latch.countDown()
                }
            }
        }

        fun await() {
            latch.await()
        }
    }
}
