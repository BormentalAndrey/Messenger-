package com.kakdela.p2p.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
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

    var text by remember { mutableStateOf("") }
    var fileName by remember { mutableStateOf("Новый файл") }
    var uri by remember { mutableStateOf<Uri?>(null) }

    var isModified by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    var isPdf by remember { mutableStateOf(false) }
    var showPdf by remember { mutableStateOf(false) }

    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pdfPageIndex by remember { mutableStateOf(0) }
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val charsets = listOf(
        Charsets.UTF_8,
        Charset.forName("windows-1251"),
        Charsets.ISO_8859_1
    )

    fun renderPdfPage(index: Int) {
        val renderer = pdfRenderer ?: return
        renderer.openPage(index).use { page ->
            val bmp = Bitmap.createBitmap(
                page.width,
                page.height,
                Bitmap.Config.ARGB_8888
            )
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            pdfBitmap = bmp
            pdfPageIndex = index
        }
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { openedUri ->
        openedUri ?: return@rememberLauncherForActivityResult

        isLoading = true

        scope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mime = resolver.getType(openedUri)

                val isPdfFile = mime == "application/pdf"
                val isDocxFile =
                    mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

                val name = openedUri.lastPathSegment?.substringAfterLast("/") ?: "Файл"

                val content = when {
                    isPdfFile -> {
                        resolver.openInputStream(openedUri)?.use {
                            PDDocument.load(it).use { pdf ->
                                PDFTextStripper().getText(pdf)
                            }
                        } ?: ""
                    }

                    isDocxFile -> {
                        resolver.openInputStream(openedUri)?.use {
                            XWPFDocument(it).use { doc ->
                                doc.paragraphs.joinToString("\n") { p -> p.text }
                            }
                        } ?: ""
                    }

                    else -> {
                        var result: String? = null
                        for (cs in charsets) {
                            try {
                                resolver.openInputStream(openedUri)?.use {
                                    val t = it.bufferedReader(cs).readText()
                                    if (t.isNotBlank()) {
                                        result = t
                                        return@use
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                        result ?: ""
                    }
                }

                withContext(Dispatchers.Main) {
                    text = content
                    uri = openedUri
                    fileName = name
                    isPdf = isPdfFile
                    showPdf = isPdfFile
                    isModified = false
                    isLoading = false
                }

                if (isPdfFile) {
                    val pfd = resolver.openFileDescriptor(openedUri, "r")!!
                    val renderer = PdfRenderer(pfd)
                    pdfRenderer = renderer
                    renderPdfPage(0)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    scope.launch {
                        snackbarHostState.showSnackbar("Ошибка открытия: ${e.localizedMessage}")
                    }
                    Log.e("Editor", "open error", e)
                }
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    ) { saveUri ->
        saveUri ?: return@rememberLauncherForActivityResult
        saveFile(context, saveUri, text, true, snackbarHostState, scope) {
            uri = saveUri
            fileName = saveUri.lastPathSegment ?: fileName
            isModified = false
        }
    }

    fun saveCurrent() {
        if (isPdf) {
            scope.launch {
                snackbarHostState.showSnackbar("PDF доступен только для чтения")
            }
            return
        }

        if (uri != null) {
            val isDocx = fileName.endsWith(".docx", true)
            saveFile(context, uri!!, text, isDocx, snackbarHostState, scope) {
                isModified = false
            }
        } else {
            saveLauncher.launch("$fileName.docx")
        }
    }

    BackHandler(isModified) {
        scope.launch {
            val res = snackbarHostState.showSnackbar(
                message = "Сохранить изменения?",
                actionLabel = "Да",
                duration = SnackbarDuration.Long
            )
            if (res == SnackbarResult.ActionPerformed) saveCurrent()
            else navController.popBackStack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isModified) "$fileName *" else fileName,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { openLauncher.launch(arrayOf("*/*")) }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Открыть")
                        }
                        IconButton(
                            onClick = { saveCurrent() },
                            enabled = !isPdf
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Сохранить")
                        }
                        if (isPdf) {
                            IconButton(onClick = { showPdf = !showPdf }) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF")
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Символов: ${text.length}", fontSize = 12.sp)
                    Text(
                        when {
                            isPdf -> "PDF (read-only)"
                            isModified -> "Не сохранено"
                            else -> "Сохранено"
                        },
                        fontSize = 12.sp,
                        color = when {
                            isPdf -> Color.Gray
                            isModified -> Color.Red
                            else -> Color(0xFF4CAF50)
                        }
                    )
                }
            }
        }
    ) { padding ->

        if (showPdf && pdfBitmap != null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    bitmap = pdfBitmap!!.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        enabled = pdfPageIndex > 0,
                        onClick = { renderPdfPage(pdfPageIndex - 1) }
                    ) { Icon(Icons.Default.ChevronLeft, contentDescription = "Назад") }

                    Text("${pdfPageIndex + 1}/${pdfRenderer?.pageCount ?: 0}")

                    IconButton(
                        enabled = pdfPageIndex < (pdfRenderer?.pageCount ?: 1) - 1,
                        onClick = { renderPdfPage(pdfPageIndex + 1) }
                    ) { Icon(Icons.Default.ChevronRight, contentDescription = "Вперед") }
                }
            }
        } else {
            TextField(
                value = text,
                onValueChange = {
                    text = it
                    if (!isPdf) isModified = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Color.White),
                textStyle = TextStyle(fontSize = 16.sp),
                readOnly = isPdf,
                colors = TextFieldDefaults.textFieldColors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }
    }
}

private fun saveFile(
    context: Context,
    uri: Uri,
    content: String,
    isDocx: Boolean,
    snackbar: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    onSuccess: () -> Unit
) {
    scope.launch(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                if (isDocx) {
                    XWPFDocument().use { doc ->
                        content.lines().forEach {
                            doc.createParagraph().createRun().setText(it)
                        }
                        doc.write(out)
                    }
                } else {
                    out.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            withContext(Dispatchers.Main) {
                onSuccess()
                scope.launch { snackbar.showSnackbar("Сохранено") }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                scope.launch {
                    snackbar.showSnackbar("Ошибка сохранения: ${e.localizedMessage}")
                }
                Log.e("Editor", "save error", e)
            }
        }
    }
}
