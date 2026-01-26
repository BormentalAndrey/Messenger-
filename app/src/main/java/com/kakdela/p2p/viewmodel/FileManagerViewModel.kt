package com.kakdela.p2p.viewmodel

import android.os.Environment
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import com.kakdela.p2p.model.FileItem
import com.kakdela.p2p.model.toFileItem
import java.io.File

class FileManagerViewModel : ViewModel() {
    private val rootPath = Environment.getExternalStorageDirectory().absolutePath
    
    var currentPath by mutableStateOf(rootPath)
    var filesList by mutableStateOf(listOf<FileItem>())

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
        }
    }

    fun goBack(): Boolean {
        if (currentPath == rootPath) return false
        val parent = File(currentPath).parentFile
        return if (parent != null) {
            currentPath = parent.absolutePath
            refresh()
            true
        } else false
    }

    fun deleteFile(fileItem: FileItem) {
        val file = File(fileItem.path)
        if (file.deleteRecursively()) refresh()
    }
}
