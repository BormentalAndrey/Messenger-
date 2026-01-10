package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
        color = Color.Black
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
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
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Ваша личность — ваши ключи.\nНикаких центральных серверов.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(64.dp))

            Button(
                onClick = onPhone,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Создать личность по номеру", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.DarkGray)
                Text(
                    text = " или ",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.DarkGray)
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = onEmailOnly,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(brush = SolidColor(Color.DarkGray)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Использовать Email (Legacy)", color = Color.White)
            }
            
            Spacer(Modifier.height(48.dp))
            
            Text(
                text = "При регистрации генерируется уникальный RSA-2048 ключ.\nНикто, кроме вас, не имеет к нему доступа.",
                fontSize = 10.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
        }
    }
}
