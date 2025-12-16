package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun RegistrationChoiceScreen(
    onEmail: () -> Unit,
    onPhone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Выберите способ входа",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onEmail,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Войти по email")
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onPhone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Войти по номеру телефона")
        }
    }
}
