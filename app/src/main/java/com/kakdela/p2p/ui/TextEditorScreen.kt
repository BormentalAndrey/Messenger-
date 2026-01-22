package com.kakdela.p2p.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var isLoading by remember { mutableStateOf(false) }

    // Список кодировок для попытки чтения текстовых файлов
    val charsets = listOf(Charsets.UTF_8, Charset.forName("windows-1251"), Charsets.ISO_8859_1)

    // Лаунчер для открытия файлов
    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { u ->
            isLoading = true
            scope.launch(Dispatchers.IO) {
                try {
                    // Извлечение расширения и имени
                    val contentResolver = context.contentResolver
                    val path = u.path ?: ""
                    val isDocx = path.endsWith(".docx", ignoreCase = true) || 
                                 u.toString().contains(".docx", ignoreCase = true)
                    
                    val nameFromUri = u.lastPathSegment?.substringAfterLast("/") ?: "Файл"
                    
                    val content: String = if (isDocx) {
                        contentResolver.openInputStream(u)?.use { input ->
                            XWPFDocument(input).use { doc ->
                                doc.paragraphs.joinToString("\n") { it.text }
                            }
                        } ?: ""
                    } else {
                        var result: String? = null
                        for (charset in charsets) {
                            try {
                                contentResolver.openInputStream(u)?.use { input ->
                                    val text = input.bufferedReader(charset).readText()
                                    // Если текст успешно прочитан и не выглядит как "мусор"
                                    if (!text.contains("")) {
                                        result = text
                                    }
                                }
                                if (result != null) break
                            } catch (_: Exception) {}
                        }
                        result ?: contentResolver.openInputStream(u)?.bufferedReader()?.readText() ?: ""
                    }
                    
                    withContext(Dispatchers.Main) {
                        textContent = content
                        currentUri = u
                        fileName = nameFromUri
                        isModified = false
                        isLoading = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        snackbarHostState.showSnackbar("Ошибка открытия: ${e.localizedMessage}")
                        Log.e("Editor", "Error opening", e)
                    }
                }
            }
        }
    }

    // Лаунчер для сохранения нового файла
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    ) { uri: Uri? ->
        uri?.let {
            saveFileProcess(
                context = context,
                uri = it,
                content = textContent,
                isDocx = it.toString().contains(".docx", ignoreCase = true),
                snackbarHostState = snackbarHostState,
                scope = scope
            ) { 
                currentUri = it
                isModified = false 
            }
        }
    }

    fun saveCurrent() {
        if (currentUri != null) {
            val isDocx = fileName.endsWith(".docx", ignoreCase = true) || 
                         currentUri.toString().contains(".docx", ignoreCase = true)
            saveFileProcess(
                context = context,
                uri = currentUri!!,
                content = textContent,
                isDocx = isDocx,
                snackbarHostState = snackbarHostState,
                scope = scope
            ) { isModified = false }
        } else {
            // Если файла еще нет, предлагаем создать .docx по умолчанию
            saveLauncher.launch(fileName.replace(".txt", ".docx"))
        }
    }

    // Обработка кнопки "Назад" при наличии несохраненных изменений
    BackHandler(enabled = isModified) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Сохранить изменения в $fileName?",
                actionLabel = "Да",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                saveCurrent()
            } else {
                navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        text = if (isModified) "$fileName *" else fileName, 
                        color = Color.Black, 
                        maxLines = 1,
                        fontSize = 16.sp 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isModified) {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Выйти без сохранения?",
                                    actionLabel = "Выйти",
                                    duration = SnackbarDuration.Short
                                )
                                if (result == SnackbarResult.ActionPerformed) navController.popBackStack()
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад", tint = Color.Black)
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 12.dp), 
                            strokeWidth = 2.dp,
                            color = Color.Black
                        )
                    } else {
                        IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Открыть", tint = Color.Black)
                        }
                        IconButton(onClick = { saveCurrent() }) {
                            Icon(Icons.Filled.Save, contentDescription = "Сохранить", tint = Color.Black)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = Color(0xFFF0F0F0), 
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Символов: ${textContent.length}",
                        color = Color.Black.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = if (isModified) "Не сохранено" else "Сохранено",
                        color = if (isModified) Color.Red else Color(0xFF4CAF50),
                        fontSize = 12.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(Color.White)
        ) {
            TextField(
                value = textContent,
                onValueChange = {
                    textContent = it
                    isModified = true
                },
                modifier = Modifier.fillMaxSize(),
                textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }
}

/**
 * Процесс сохранения файла (общий для текстовых документов и Word)
 */
private fun saveFileProcess(
    context: Context,
    uri: Uri,
    content: String,
    isDocx: Boolean,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            // Используем "w" для перезаписи текстовых файлов и бинарный поток для docx
            val mode = if (isDocx) "w" else "wt"
            
            context.contentResolver.openOutputStream(uri, mode)?.use { output ->
                if (isDocx) {
                    XWPFDocument().use { doc ->
                        // Разбиваем текст по строкам и создаем абзацы в Word
                        content.split("\n").forEach { line ->
                            val paragraph = doc.createParagraph()
                            val run = paragraph.createRun()
                            run.setText(line)
                        }
                        doc.write(output)
                    }
                } else {
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            withContext(Dispatchers.Main) {
                onSuccess()
                snackbarHostState.showSnackbar("Сохранено успешно")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val errorMsg = e.localizedMessage ?: "Неизвестная ошибка"
                snackbarHostState.showSnackbar("Ошибка сохранения: $errorMsg")
                Log.e("Editor", "Save error", e)
            }
        }
    }
}
