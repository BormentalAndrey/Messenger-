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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
    ) { uri: Uri? ->
        uri?.let { u ->
            scope.launch {
                try {
                    val extension = u.lastPathSegment?.substringAfterLast(".")?.lowercase()
                    val content: String = when (extension) {
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
                    textContent = content
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
    ) { uri: Uri? ->
        uri?.let {
            saveFile(
                context, it, textContent,
                it.lastPathSegment?.endsWith(".docx", ignoreCase = true) == true,
                snackbarHostState
            )
            isModified = false
        }
    }

    fun saveCurrent() {
        currentUri?.let { uri ->
            saveFile(
                context, uri, textContent,
                uri.lastPathSegment?.endsWith(".docx", ignoreCase = true) == true,
                snackbarHostState
            )
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
            if (result == SnackbarResult.ActionPerformed) saveCurrent()
            else navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(fileName, color = Color.Black) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.Black)
                    }
                },
                actions = {
                    IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color.Black)
                    }
                    IconButton(onClick = { saveCurrent() }) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = Color.Black)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = Color(0xFFF0F0F0), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Символов: ${textContent.length} • ${if (isModified) "Изменено" else "Сохранено"}",
                    color = Color.Black.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    ) { paddingValues ->
        TextField(
            value = textContent,
            onValueChange = {
                textContent = it
                isModified = true
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .background(Color.White)
                .padding(16.dp),
            textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
            singleLine = false
        )
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
