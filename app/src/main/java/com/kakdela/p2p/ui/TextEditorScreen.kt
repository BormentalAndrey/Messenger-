package com.kakdela.p2p.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(navController: NavHostController) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Новый файл") }
    var isModified by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(16.sp) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val charsets = listOf(Charsets.UTF_8, Charset.forName("windows-1251"), Charset.forName("KOI8-R"), Charsets.ISO_8859_1)

    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val content = when (uri.pathSegments.last().substringAfterLast(".", "").lowercase()) {
                        "docx" -> {
                            navController.context.contentResolver.openInputStream(uri)?.use { input ->
                                XWPFDocument(input).use { doc ->
                                    doc.paragraphs.joinToString("\n") { it.text }
                                }
                            } ?: ""
                        }
                        "pdf" -> {
                            navController.context.contentResolver.openInputStream(uri)?.use { input ->
                                PDDocument.load(input).use { doc ->
                                    PDFTextStripper().getText(doc)
                                }
                            } ?: ""
                        }
                        else -> { // txt и другие
                            var content: String?
                            for (charset in charsets) {
                                try {
                                    content = navController.context.contentResolver.openInputStream(uri)?.bufferedReader(charset)?.readText()
                                    if (content != null) break
                                } catch (e: Exception) {
                                    continue
                                }
                            }
                            content ?: ""
                        }
                    }
                    text = TextFieldValue(content)
                    currentUri = uri
                    fileName = uri.pathSegments.last()
                    isModified = false
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Ошибка открытия: ${e.message ?: ""}")
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { saveFile(navController.context, it, text.text, isDocx = false, snackbarHostState) }
    }

    val saveAsDocxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri ->
        uri?.let { saveFile(navController.context, it, text.text, isDocx = true, snackbarHostState) }
    }

    fun saveCurrent() {
        currentUri?.let { uri ->
            if (uri.pathSegments.last().endsWith(".docx", ignoreCase = true)) {
                saveFile(navController.context, uri, text.text, isDocx = true, snackbarHostState)
            } else {
                saveFile(navController.context, uri, text.text, isDocx = false, snackbarHostState)
            }
            isModified = false
        } ?: saveLauncher.launch("Новый файл.txt")
    }

    BackHandler(isModified) {
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                "Сохранить изменения?",
                actionLabel = "Сохранить",
                withDismissAction = true
            )
            when (result) {
                SnackbarResult.ActionPerformed -> saveCurrent()
                SnackbarResult.Dismissed -> navController.popBackStack()
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(fileName, modifier = Modifier.basicMarquee(), color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FolderOpen, null, tint = Color.Cyan)
                    }
                    IconButton(onClick = { saveCurrent() }) {
                        Icon(Icons.Filled.Save, null, tint = Color.Magenta)
                    }
                    var expanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Filled.SaveAs, null, tint = Color.Green)
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(text = { Text("Сохранить как TXT") }, onClick = {
                                saveLauncher.launch(fileName.ifEmpty { "Новый файл.txt" })
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text("Сохранить как DOCX") }, onClick = {
                                saveAsDocxLauncher.launch(fileName.replace(".txt", ".docx", ignoreCase = true))
                                expanded = false
                            })
                            DropdownMenuItem(text = { Text("Размер шрифта") }, onClick = { expanded = false }) {
                                // Можно расширить отдельным диалогом со списком размеров
                                var sizeExpanded by remember { mutableStateOf(false) }
                                DropdownMenu(expanded = sizeExpanded, onDismissRequest = { sizeExpanded = false }) {
                                    listOf(12, 14, 16, 18, 20, 24).forEach { size ->
                                        DropdownMenuItem(text = { Text("${size} sp") }, onClick = {
                                            fontSize = size.sp
                                            sizeExpanded = false
                                        })
                                    }
                                }
                                IconButton(onClick = { sizeExpanded = !sizeExpanded }) {
                                    Icon(Icons.Filled.FontDownload, null)
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(color = Color(0xFF111111), border = BorderStroke(1.dp, Color.Magenta.copy(0.3f))) {
                Text(
                    "Символов: ${text.text.length} | Изменено: $isModified",
                    color = Color.White.copy(0.7f),
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    fontSize = 12.sp
                )
            }
        }
    ) { padding ->
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                isModified = true
            },
            textStyle = TextStyle(fontSize = fontSize, color = Color.White),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .padding(16.dp)
        )
    }
}

private fun saveFile(context: Context, uri: Uri, content: String, isDocx: Boolean, snackbar: SnackbarHostState) {
    kotlinx.coroutines.runBlocking {
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
            snackbar.showSnackbar("Сохранено")
        } catch (e: Exception) {
            snackbar.showSnackbar("Ошибка сохранения: ${e.message}")
        }
    }
}
