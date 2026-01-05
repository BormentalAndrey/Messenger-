package com.kakdela.p2p.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.InputStream
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(navController: NavHostController) {
    val context = LocalContext.current
    var textContent by remember { mutableStateOf("Откройте .txt или .docx файл") }
    var currentUri by remember { mutableStateOf<Uri?>(null) }

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            currentUri = it
            textContent = readDocument(context, it)
        }
    }

    // Лаунчер для сохранения (создания файла)
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { saveDocument(context, it, textContent) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор DOCX/TXT") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { openLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileOpen, "Открыть")
                    }
                    IconButton(onClick = {
                        if (currentUri != null) saveDocument(context, currentUri!!, textContent)
                        else saveLauncher.launch("document.txt")
                    }) {
                        Icon(Icons.Default.Save, "Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        TextField(
            value = textContent,
            onValueChange = { textContent = it },
            modifier = Modifier.fillMaxSize().padding(padding).background(Color.White),
            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent)
        )
    }
}

private fun readDocument(context: Context, uri: Uri): String {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val fileName = uri.path?.lowercase() ?: ""
            val mimeType = context.contentResolver.getType(uri) ?: ""

            if (mimeType.contains("wordprocessingml") || fileName.endsWith(".docx")) {
                // Чтение DOCX через Apache POI
                val doc = XWPFDocument(inputStream)
                val sb = StringBuilder()
                doc.paragraphs.forEach { sb.append(it.text).append("\n") }
                sb.toString()
            } else {
                // Чтение обычного текста
                inputStream.bufferedReader().readText()
            }
        } ?: "Ошибка доступа"
    } catch (e: Exception) {
        "Ошибка чтения: ${e.message}"
    }
}

private fun saveDocument(context: Context, uri: Uri, content: String) {
    try {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray())
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

