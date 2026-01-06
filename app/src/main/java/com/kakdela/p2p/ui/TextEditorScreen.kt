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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

@Composable
fun TextEditorScreen(navController: NavController) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val reader = BufferedReader(
                    InputStreamReader(context.contentResolver.openInputStream(it))
                )
                text = reader.readText()
                reader.close()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка открытия файла", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { filePicker.launch("text/plain") }) {
                Text("Открыть .txt")
            }
            Button(onClick = { saveText(context, text) }) {
                Text("Сохранить")
            }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color(0xFF1A1A1A),
                unfocusedContainerColor = Color(0xFF1A1A1A)
            )
        )
    }
}

private fun saveText(context: Context, content: String) {
    try {
        val file = context.getExternalFilesDir(null)
            ?.resolve("Text_${System.currentTimeMillis()}.txt")

        val writer = OutputStreamWriter(file!!.outputStream())
        writer.write(content)
        writer.close()

        Toast.makeText(context, "Сохранено: ${file.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
    }
}
