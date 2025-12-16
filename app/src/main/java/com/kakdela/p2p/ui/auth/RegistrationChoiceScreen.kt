package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            fontSize = 26.sp
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onEmail,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("По email (Развлечения)")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onPhone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("По номеру телефона")
        }
    }
}
