package com.kakdela.p2p.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var textContent by remember { mutableStateOf("") }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Новый файл.txt") }
    var isModified by remember { mutableStateOf(false) }

    val charsets = listOf(Charsets.UTF_8, Charset.forName("windows-1251"), Charsets.ISO_8859_1)

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { u ->
            scope.launch {
                try {
                    val extension = u.lastPathSegment?.substringAfterLast(".")?.lowercase()
                    textContent = when (extension) {
                        "docx" -> {
                            context.contentResolver.openInputStream(u)?.use { input ->
                                XWPFDocument(input).use { doc ->
                                    doc.paragraphs.joinToString("\n") { it.text }
                                }
                            } ?: ""
                        }
                        else -> {
                            var result: String? = null
                            context.contentResolver.openInputStream(u)?.use { input ->
                                for (charset in charsets) {
                                    try {
                                        result = input.bufferedReader(charset).readText()
                                        break
                                    } catch (_: Exception) {}
                                }
                            }
                            result ?: ""
                        }
                    }
                    currentUri = u
                    fileName = u.lastPathSegment ?: "Файл"
                    isModified = false
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Ошибка открытия: ${e.localizedMessage}")
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        uri?.let {
            saveFile(context, it, textContent, it.lastPathSegment?.endsWith(".docx", true) == true, snackbarHostState)
            isModified = false
        }
    }

    fun saveCurrent() {
        currentUri?.let { uri ->
            saveFile(context, uri, textContent, uri.lastPathSegment?.endsWith(".docx", true) == true, snackbarHostState)
            isModified = false
        } ?: saveLauncher.launch(fileName)
    }

    BackHandler(enabled = isModified) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Сохранить изменения?",
                actionLabel = "Сохранить",
                withDismissAction = true
            )
            when (result) {
                SnackbarResult.ActionPerformed -> saveCurrent()
                else -> navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(fileName, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, null, tint = Color.Black)
                    }
                },
                actions = {
                    IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FolderOpen, null, tint = Color.Black)
                    }
                    IconButton(onClick = { saveCurrent() }) {
                        Icon(Icons.Filled.Save, null, tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            TextField(
                value = textContent,
                onValueChange = {
                    textContent = it
                    isModified = true
                },
                modifier = Modifier
                    .fillMaxSize(),
                textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                placeholder = { Text("Введите текст...") }
            )
        }
    }
}

private fun saveFile(
    context: Context,
    uri: Uri,
    content: String,
    isDocx: Boolean,
    snackbarHostState: SnackbarHostState
) {
    kotlinx.coroutines.MainScope().launch {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                if (isDocx) {
                    XWPFDocument().use { doc ->
                        content.split("\n").forEach { line ->
                            if (line.isNotBlank()) {
                                doc.createParagraph().createRun().setText(line)
                            } else {
                                doc.createParagraph()
                            }
                        }
                        doc.write(output)
                    }
                } else {
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            snackbarHostState.showSnackbar("Сохранено успешно")
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Ошибка сохранения: ${e.localizedMessage}")
        }
    }
}
