package com.kakdela.p2p.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.nio.charset.Charset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var text by remember { mutableStateOf("") }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("Новый файл.txt") }
    var isModified by remember { mutableStateOf(false) }
    var fontSize by remember { mutableStateOf(16.sp) }

    val charsets = listOf(Charsets.UTF_8, Charset.forName("windows-1251"), Charsets.ISO_8859_1)

    val recognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

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
                        "pdf" -> {
                            // Извлечение текста из PDF через рендеринг страниц + OCR (ML Kit)
                            val pfd = context.contentResolver.openFileDescriptor(u, "r") ?: return@let
                            val renderer = PdfRenderer(pfd)
                            val fullText = StringBuilder()

                            for (i in 0 until renderer.pageCount) {
                                val page = renderer.openPage(i)
                                val bitmap = Bitmap.createBitmap(
                                    page.width,
                                    page.height,
                                    Bitmap.Config.ARGB_8888
                                )
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val image = InputImage.fromBitmap(bitmap, 0)
                                val result = recognizer.process(image).await() // suspend функция

                                fullText.append(result.text).append("\n\n")

                                bitmap.recycle()
                                page.close()
                            }
                            renderer.close()
                            pfd.close()

                            fullText.toString()
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

                    text = content
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
            saveFile(context, it, text, it.lastPathSegment?.endsWith(".docx", ignoreCase = true) == true, snackbarHostState)
            isModified = false
        }
    }

    fun saveCurrent() {
        currentUri?.let { uri ->
            saveFile(context, uri, text, uri.lastPathSegment?.endsWith(".docx", ignoreCase = true) == true, snackbarHostState)
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
                title = { Text(fileName, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Color.Cyan)
                    }
                    IconButton(onClick = { saveCurrent() }) {
                        Icon(Icons.Filled.Save, contentDescription = null, tint = Color.Magenta)
                    }
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Filled.TextFields, contentDescription = null, tint = Color.Green)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Сохранить как...") },
                            onClick = {
                                saveLauncher.launch(fileName)
                                menuExpanded = false
                            }
                        )
                        Divider()
                        listOf(12, 14, 16, 18, 20, 24).forEach { size ->
                            DropdownMenuItem(
                                text = { Text("$size sp") },
                                onClick = {
                                    fontSize = size.sp
                                    menuExpanded = false
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = Color(0xFF111111),
                border = BorderStroke(1.dp, Color.Magenta.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "Символов: ${text.length} • ${if (isModified) "Изменено" else "Сохранено"}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }
        }
    ) { paddingValues ->
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                isModified = true
            },
            textStyle = TextStyle(color = Color.White, fontSize = fontSize),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(Color.Black)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
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
    val scope = kotlinx.coroutines.MainScope()
    scope.launch {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                if (isDocx) {
                    XWPFDocument().use { doc ->
                        val lines = content.split("\n")
                        for (line in lines) {
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
