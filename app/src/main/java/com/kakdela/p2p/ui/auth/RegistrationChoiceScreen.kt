package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RegistrationChoiceScreen(
    onPhone: () -> Unit,
    onEmailOnly: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black // Принудительный черный фон для P2P стиля
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "KakDela P2P",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Cyan,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Децентрализованная сеть общения.\nВаша личность хранится только у вас.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(64.dp))

            // Основная кнопка регистрации
            Button(
                onClick = onPhone,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Создать личность по номеру", 
                    color = Color.Black, 
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Divider(modifier = Modifier.weight(1f), color = Color.DarkGray)
                Text(
                    text = " или ",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Divider(modifier = Modifier.weight(1f), color = Color.DarkGray)
            }

            Spacer(Modifier.height(20.dp))

            // Legacy вход
            OutlinedButton(
                onClick = onEmailOnly,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(Color.DarkGray)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Войти через Email (Legacy)", color = Color.White)
            }
            
            Spacer(Modifier.height(32.dp))
            
            Text(
                text = "Нажимая кнопку, вы генерируете уникальный приватный ключ RSA-2048 на этом устройстве.",
                fontSize = 10.sp,
                color = Color(0xFF444444),
                textAlign = TextAlign.Center
            )
        }
    }
}
