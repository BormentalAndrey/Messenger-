package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun RegistrationChoiceScreen(
    onPhone: () -> Unit,
    onEmailOnly: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Создайте свой P2P профиль",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(Modifier.height(48.dp))

            Button(
                onClick = onPhone,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Создать личность по номеру")
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "или",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onEmailOnly,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Войти через Email (Legacy)")
            }
        }
    }
}
