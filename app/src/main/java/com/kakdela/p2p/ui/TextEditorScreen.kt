package com.kakdela.p2p.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditorDefaults
import kotlinx.coroutines.launch
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val richTextState: RichTextState = rememberRichTextState()

    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Новый файл.txt") }
    var isModified by remember { mutableStateOf(false) }

    val charsets = listOf(Charsets.UTF_8, Charset.forName("windows-1251"), Charsets.ISO_8859_1)

    // Цвета для выбора (можно расширить)
    val availableColors = listOf(
        Color.Black, Color.Red, Color.Blue, Color.Green, Color(0xFFFF5722), Color(0xFF9C27B0)
    )

    // Размеры шрифта
    val availableSizes = listOf(12.sp, 14.sp, 16.sp, 18.sp, 20.sp, 24.sp, 28.sp, 32.sp)

    val openLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
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

                    richTextState.setPlainText(content)
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
            saveFile(context, it, richTextState.toPlainText(), it.lastPathSegment?.endsWith(".docx", ignoreCase = true) == true, snackbarHostState)
            isModified = false
        }
    }

    fun saveCurrent() {
        currentUri?.let { uri ->
            saveFile(context, uri, richTextState.toPlainText(), uri.lastPathSegment?.endsWith(".docx", ignoreCase = true) == true, snackbarHostState)
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

                    // Toolbar с форматированием
                    Row {
                        IconButton(onClick = { richTextState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }) {
                            Icon(Icons.Filled.FormatBold, contentDescription = "Жирный")
                        }
                        IconButton(onClick = { richTextState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }) {
                            Icon(Icons.Filled.FormatItalic, contentDescription = "Курсив")
                        }
                        IconButton(onClick = { richTextState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }) {
                            Icon(Icons.Filled.FormatUnderlined, contentDescription = "Подчёркивание")
                        }

                        var colorExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { colorExpanded = true }) {
                            Icon(Icons.Filled.FormatColorText, contentDescription = "Цвет текста")
                        }
                        DropdownMenu(expanded = colorExpanded, onDismissRequest = { colorExpanded = false }) {
                            availableColors.forEach { color ->
                                DropdownMenuItem(
                                    text = { Box(modifier = Modifier.size(24.dp).background(color)) },
                                    onClick = {
                                        richTextState.toggleSpanStyle(SpanStyle(color = color))
                                        colorExpanded = false
                                    }
                                )
                            }
                        }

                        var sizeExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { sizeExpanded = true }) {
                            Icon(Icons.Filled.TextFields, contentDescription = "Размер шрифта")
                        }
                        DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                            availableSizes.forEach { size ->
                                DropdownMenuItem(
                                    text = { Text("${size.value.toInt()} sp") },
                                    onClick = {
                                        richTextState.toggleSpanStyle(SpanStyle(fontSize = size))
                                        sizeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = Color(0xFFF0F0F0), border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))) {
                Text(
                    text = "Символов: ${richTextState.toPlainText().length} • ${if (isModified) "Изменено" else "Сохранено"}",
                    color = Color.Black.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }
    ) { paddingValues ->
        RichTextEditor(
            state = richTextState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            textStyle = RichTextEditorDefaults.textStyle(color = Color.Black, fontSize = 16.sp),
            onValueChange = { isModified = true }
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
