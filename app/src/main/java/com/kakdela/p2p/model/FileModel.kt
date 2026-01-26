package com.kakdela.p2p.model

import java.io.File

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

fun File.toFileItem() = FileItem(
    name = this.name,
    path = this.absolutePath,
    isDirectory = this.isDirectory,
    size = this.length(),
    lastModified = this.lastModified()
)
