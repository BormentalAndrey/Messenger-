package com.kakdela.p2p.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    var status by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(Color.Black)  // Чтобы соответствовать неоновой теме
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        // Аватарка (заглушка — позже можно добавить загрузку фото)
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "А",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(16.dp))

        Text("Аватарка (нажмите, чтобы изменить — скоро)", color = Color.Gray)

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = status,
            onValueChange = { status = it },
            label = { Text("Статус") },
            placeholder = { Text("В сети") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Оповещения о новых сообщениях", color = Color.White)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = Color.Gray
                )
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Беззвучные уведомления для ЧёКаВо?", color = Color.White)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = true,  // По умолчанию отключены для глобального чата
                onCheckedChange = { /* можно сохранить в настройках */ },
                enabled = false  // Пока фиксировано
            )
        }
    }
}
