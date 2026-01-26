package com.kakdela.p2p.viewmodel

import android.content.Context
import android.widget.Toast
import android.os.Environment
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.kakdela.p2p.model.FileItem
import com.kakdela.p2p.model.toFileItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class FileManagerViewModel : ViewModel() {
    private val rootPath = Environment.getExternalStorageDirectory().absolutePath

    var currentPath by mutableStateOf(rootPath)
    var filesList by mutableStateOf(listOf<FileItem>())

    var selectedFiles by mutableStateOf(setOf<FileItem>())
    val isSelectionMode: Boolean get() = selectedFiles.isNotEmpty()

    init {
        refresh()
    }

    fun refresh() {
        val dir = File(currentPath)
        filesList = dir.listFiles()?.map { it.toFileItem() }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
    }

    fun navigateTo(path: String) {
        val file = File(path)
        if (file.isDirectory) {
            currentPath = path
            refresh()
            clearSelection()
        }
    }

    fun pathUpToIndex(index: Int): String {
        val parts = currentPath.split("/").filter { it.isNotEmpty() }
        return "/" + parts.take(index).joinToString("/")
    }

    fun goBack(): Boolean {
        if (currentPath == rootPath) return false
        val parent = File(currentPath).parentFile
        return if (parent != null) {
            currentPath = parent.absolutePath
            refresh()
            clearSelection()
            true
        } else false
    }

    fun toggleSelection(item: FileItem) {
        selectedFiles = if (selectedFiles.contains(item)) selectedFiles - item else selectedFiles + item
    }

    fun clearSelection() {
        selectedFiles = emptySet()
    }

    fun deleteFileWithConfirmation(fileItem: FileItem) {
        CoroutineScope(Dispatchers.IO).launch {
            val file = File(fileItem.path)
            if (file.deleteRecursively()) refresh()
        }
    }

    fun renameFile(fileItem: FileItem, newName: String) {
        val file = File(fileItem.path)
        val renamed = File(file.parentFile, newName)
        if (file.renameTo(renamed)) refresh()
    }

    fun copyFile(fileItem: FileItem) {
        val file = File(fileItem.path)
        val copy = File(file.parentFile, file.name + "_copy")
        file.copyRecursively(copy, overwrite = true)
        refresh()
    }

    fun createFolder(name: String) {
        val folder = File(currentPath, name)
        if (!folder.exists()) folder.mkdirs()
        refresh()
    }

    fun showFileProperties(fileItem: FileItem, context: Context) {
        val file = File(fileItem.path)
        val message = """
            Имя: ${file.name}
            Путь: ${file.absolutePath}
            Размер: ${file.length() / 1024} KB
            Последнее изменение: ${file.lastModified()}
            Директория: ${file.isDirectory}
        """.trimIndent()
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun moveFilesTo(targetFolder: FileItem) {
        if (!targetFolder.isDirectory) return
        CoroutineScope(Dispatchers.IO).launch {
            selectedFiles.forEach { fileItem ->
                val file = File(fileItem.path)
                val dest = File(targetFolder.path, file.name)
                file.renameTo(dest)
            }
            clearSelection()
            refresh()
        }
    }
}
