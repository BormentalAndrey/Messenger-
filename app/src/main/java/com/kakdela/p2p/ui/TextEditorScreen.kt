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
import com.tomroush.pdfbox.android.PDFBoxResourceLoader
import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.text.PDFTextStripper
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(navController: NavHostController) {
    val context = LocalContext.current
    
    // Инициализация ресурсов PDFBox (необходимо для работы библиотеки на Android)
    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context)
    }

    var textContent by remember { mutableStateOf("Откройте файл для чтения...") }
    
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            textContent = readTextFromUri(context, it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Редактор") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Открыть")
                    }
                    IconButton(onClick = { /* Логика сохранения (для TXT) */ }) {
                        Icon(Icons.Default.Save, contentDescription = "Сохранить")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
            TextField(
                value = textContent,
                onValueChange = { textContent = it },
                modifier = Modifier.fillMaxSize(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    }
}

private fun readTextFromUri(context: Context, uri: Uri): String {
    return try {
        val contentResolver = context.contentResolver
        val inputStream: InputStream? = contentResolver.openInputStream(uri)
        val mimeType = contentResolver.getType(uri)
        
        // Проверяем, является ли файл PDF по расширению или MIME-типу
        if (mimeType == "application/pdf" || uri.path?.endsWith(".pdf", ignoreCase = true) == true) {
            inputStream.use { stream ->
                val document = PDDocument.load(stream)
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)
                document.close()
                text
            }
        } else {
            // Для обычных текстовых файлов
            inputStream?.bufferedReader()?.use { it.readText() } ?: "Файл пуст или недоступен"
        }
    } catch (e: Exception) {
        "Ошибка при чтении: ${e.localizedMessage}"
    }
}

