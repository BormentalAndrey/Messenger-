package com.kakdela.p2p.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    var status by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // Аватарка (заглушка — можно добавить ImagePicker)
        Text("Аватарка (скоро)")

        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = status,
            onValueChange = { status = it },
            label = { Text("Статус") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Оповещения о сообщениях")
            Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
        }
    }
}
