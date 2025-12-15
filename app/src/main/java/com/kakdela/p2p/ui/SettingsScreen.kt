package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(navController: NavHostController) {

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {

        Button(
            onClick = { navController.navigate("auth_phone") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Вход по телефону")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { navController.navigate("auth_email") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Вход по email")
        }
    }
}
@Composable
fun SettingsScreen() {
    var status by remember { mutableStateOf("") }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(32.dp))

        // Заглушка аватарки
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

        Text("Аватарка (скоро)", color = Color.Gray)

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
            Text("Оповещения о сообщениях", color = Color.White)
            Spacer(Modifier.weight(1f))
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { notificationsEnabled = it }
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
                checked = true,
                onCheckedChange = null,
                enabled = false
            )
        }
    }
}
