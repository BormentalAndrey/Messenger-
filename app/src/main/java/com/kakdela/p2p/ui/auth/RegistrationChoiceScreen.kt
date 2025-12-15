package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

@Composable
fun RegistrationChoiceScreen(navController: NavHostController) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Выберите способ входа", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = { navController.navigate("auth_email") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("По email (Развлечения: ЧёКаВо? + Pikabu)")
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { navController.navigate("auth_phone") },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("По номеру телефона (Личные чаты + Контакты)")
        }
    }
}
