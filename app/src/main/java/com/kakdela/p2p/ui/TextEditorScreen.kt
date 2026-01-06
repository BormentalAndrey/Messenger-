package com.kakdela.p2p.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import org.apache.poi.xwpf.usermodel.XWPFDocument // Необходима зависимость в build.gradle
import java.io.FileOutputStream

@Composable
fun TextEditorScreen(navController: NavController) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { 
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val doc = XWPFDocument(inputStream)
                val sb = StringBuilder()
                // Исправлено: явный итератор для предотвращения конфликта типов
                doc.paragraphs.forEach { para ->
                    sb.append(para.text).append("\n")
                }
                text = sb.toString()
                inputStream?.close()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка чтения .docx", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { filePicker.launch("application/vnd.openxmlformats-officedocument.wordprocessingml.document") }) {
                Text("Открыть .docx")
            }
            Button(onClick = { saveToDocx(context, text) }) {
                Text("Сохранить")
            }
        }
        
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxSize().padding(top = 16.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1A1A1A)
            )
        )
    }
}

private fun saveToDocx(context: Context, content: String) {
    try {
        val document = XWPFDocument()
        val paragraph = document.createParagraph()
        val run = paragraph.createRun()
        run.setText(content)
        
        val file = java.io.File(context.getExternalFilesDir(null), "Document_${System.currentTimeMillis()}.docx")
        val out = FileOutputStream(file)
        document.write(out)
        out.close()
        Toast.makeText(context, "Сохранено: ${file.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
    }
}

