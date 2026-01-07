package com.kakdela.p2p.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile
import org.json.JSONObject

class FileTransferWorker(
    private val context: Context,
    private val identityRepo: IdentityRepository
) {
    private val CHUNK_SIZE = 16384 // 16 KB
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val seedingFiles = mutableMapOf<String, File>()

    init {
        identityRepo.onSignalingMessageReceived = { type, data, fromIp ->
            if (type == "FILE_CHUNK_REQ") {
                handleChunkRequest(data, fromIp)
            }
        }
    }

    private fun handleChunkRequest(jsonData: String, targetIp: String) {
        scope.launch {
            try {
                val json = JSONObject(jsonData)
                val fileId = json.getString("file_id")
                val chunkIndex = json.getInt("chunk_index")
                val file = seedingFiles[fileId] ?: return@launch

                RandomAccessFile(file, "r").use { raf ->
                    val pos = chunkIndex.toLong() * CHUNK_SIZE
                    if (pos < file.length()) {
                        raf.seek(pos)
                        val buffer = ByteArray(CHUNK_SIZE)
                        val bytesRead = raf.read(buffer)
                        
                        val chunk = if (bytesRead == CHUNK_SIZE) buffer else buffer.copyOfRange(0, bytesRead)
                        
                        // ВАЖНО: передаем именно ByteArray
                        identityRepo.sendSignalingData(targetIp, "FILE_CHUNK_DATA", chunk)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun downloadFileP2P(targetIp: String, fileId: String, fileName: String, totalChunks: Int): File = withContext(Dispatchers.IO) {
        val outputFile = File(context.getExternalFilesDir(null), fileName)
        RandomAccessFile(outputFile, "rw").use { raf ->
            raf.setLength(totalChunks.toLong() * CHUNK_SIZE)

            for (i in 0 until totalChunks) {
                val request = JSONObject().apply {
                    put("file_id", fileId)
                    put("chunk_index", i)
                }.toString()
                
                // Здесь передаем строку запроса, упакованную в ByteArray для единообразия
                identityRepo.sendSignalingData(targetIp, "FILE_CHUNK_REQ", request.toByteArray())
                delay(100) 
            }
        }
        outputFile
    }

    fun addFileToSeeding(file: File): String {
        val id = java.util.UUID.randomUUID().toString()
        seedingFiles[id] = file
        return id
    }
}

