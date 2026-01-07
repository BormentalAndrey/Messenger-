package com.kakdela.p2p.ui

import android.content.Context
import android.net.Uri
import android.util.Log
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

    val charsets = listOf(Charsets.UTF_8, Charset.forName("windows-1251"), Charsets.ISO_8859_1)

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { u ->
            isLoading = true
            scope.launch(Dispatchers.IO) { // ВАЖНО: Работаем в IO потоке
                try {
                    val extension = u.lastPathSegment?.substringAfterLast(".")?.lowercase()
                    // Получаем имя файла безопасно
                    val newName = u.path?.split("/")?.last() ?: "Файл"
                    
                    val content: String = when (extension) {
                        "docx" -> {
                            try {
                                context.contentResolver.openInputStream(u)?.use { input ->
                                    XWPFDocument(input).use { doc ->
                                        doc.paragraphs.joinToString("\n") { it.text }
                                    }
                                } ?: ""
                            } catch (e: NoClassDefFoundError) {
                                throw Exception("Библиотека DOCX не поддерживается вашим устройством")
                            }
                        }
                        else -> {
                            var result: String? = null
                            // ВАЖНО: Открываем поток заново для каждой кодировки
                            for (charset in charsets) {
                                try {
                                    context.contentResolver.openInputStream(u)?.use { input ->
                                        val text = input.bufferedReader(charset).readText()
                                        // Эвристика: если нет странных символов, считаем успехом
                                        if (!text.contains("")) {
                                            result = text
                                        }
                                    }
                                    if (result != null) break
                                } catch (_: Exception) {}
                            }
                            // Если не вышло подобрать, читаем как UTF-8
                            result ?: context.contentResolver.openInputStream(u)?.bufferedReader()?.readText() ?: ""
                        }
                    }
                    
                    withContext(Dispatchers.Main) {
                        textContent = content
                        currentUri = u
                        fileName = newName
                        isModified = false
                        isLoading = false
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isLoading = false
                        snackbarHostState.showSnackbar("Ошибка: ${e.localizedMessage}")
                        Log.e("Editor", "Error opening", e)
                    }
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        uri?.let {
            saveFileProcess(
                context, it, textContent,
                it.toString().endsWith(".docx", ignoreCase = true),
                snackbarHostState,
                scope
            ) { isModified = false }
        }
    }

    fun saveCurrent() {
        if (currentUri != null) {
            saveFileProcess(
                context, currentUri!!, textContent,
                fileName.endsWith(".docx", ignoreCase = true),
                snackbarHostState,
                scope
            ) { isModified = false }
        } else {
            saveLauncher.launch(fileName)
        }
    }

    BackHandler(enabled = isModified) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Сохранить изменения?",
                actionLabel = "Сохранить",
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) saveCurrent()
            else navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        fileName, 
                        color = Color.Black, 
                        maxLines = 1,
                        fontSize = 16.sp 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.Black)
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color.Black)
                        }
                        IconButton(onClick = { saveCurrent() }) {
                            Icon(Icons.Filled.Save, contentDescription = null, tint = Color.Black)
                        }
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
        // ИСПРАВЛЕНИЕ Layout: Убрали verticalScroll из TextField и добавили его в Box
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
                modifier = Modifier.fillMaxSize(), // TextField сам умеет скроллить контент
                textStyle = TextStyle(color = Color.Black, fontSize = 16.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    disabledContainerColor = Color.White
                )
            )
        }
    }
}

private fun saveFileProcess(
    context: Context,
    uri: Uri,
    content: String,
    isDocx: Boolean,
    snackbarHostState: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit
) {
    scope.launch(Dispatchers.IO) { // ВАЖНО: Сохранение в IO потоке
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                if (isDocx) {
                    try {
                        XWPFDocument().use { doc ->
                            content.split("\n").forEach { line ->
                                val p = doc.createParagraph()
                                val r = p.createRun()
                                r.setText(line)
                            }
                            doc.write(output)
                        }
                    } catch (e: NoClassDefFoundError) {
                        throw Exception("Ошибка библиотек POI")
                    }
                } else {
                    output.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            withContext(Dispatchers.Main) {
                onSuccess()
                snackbarHostState.showSnackbar("Файл сохранен")
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                snackbarHostState.showSnackbar("Ошибка сохранения: ${e.localizedMessage}")
            }
        }
    }
}

