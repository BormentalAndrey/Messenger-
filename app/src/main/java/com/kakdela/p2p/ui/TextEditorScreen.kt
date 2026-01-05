package com.kakdela.p2p.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tomroush.pdfbox.pdmodel.PDDocument
import com.tomroush.pdfbox.text.PDFTextStripper
import com.tomroush.pdfbox.android.PDFBoxResourceLoader
import java.io.InputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen() {
    val context = LocalContext.current
    // Инициализация PDFBox для Android
    LaunchedEffect(Unit) {
        PDFBoxResourceLoader.init(context)
    }

    var textContent by remember { mutableStateOf("") }
    
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
                actions = {
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "Открыть")
                    }
                    IconButton(onClick = { /* Логика сохранения */ }) {
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
        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        val fileName = uri.path ?: ""
        
        if (fileName.endsWith(".pdf", ignoreCase = true) || 
            context.contentResolver.getType(uri)?.contains("pdf") == true) {
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            text
        } else {
            inputStream?.bufferedReader()?.use { it.readText() } ?: "Ошибка чтения"
        }
    } catch (e: Exception) {
        "Ошибка: ${e.localizedMessage}"
    }
}

