package com.kakdela.p2p.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // Важно: учет системных отступов
                .verticalScroll(rememberScrollState()) // Важно: скролл для маленьких экранов
                .padding(horizontal = 32.dp, vertical = 24.dp), // Vertical padding вместо Spacer сверху/снизу
            horizontalAlignment = Alignment.CenterHorizontally,
            // Удаляем verticalArrangement = Arrangement.Center, используем Spacer с весом или просто скролл
            verticalArrangement = Arrangement.Center
        ) {
            // Если экран очень высокий, этот спейсер отодвинет контент чуть вниз, но при скролле не помешает
            Spacer(Modifier.height(24.dp))

            Text(
                text = "Как дела?",
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
            
            // Уменьшили отступ с 64 до 48, чтобы экономить место
            Spacer(Modifier.height(48.dp))

            // Кнопка 1: Используем heightIn вместо фиксированного height
            Button(
                onClick = onPhone,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp), 
                colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Создать личность по номеру", 
                    color = Color.Black, 
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center // Центрируем если будет перенос
                )
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

            // Кнопка 2: Legacy Email
            OutlinedButton(
                onClick = onEmailOnly,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                border = BorderStroke(1.dp, Color.DarkGray), // Исправлено создание Border
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Использовать Email (Legacy)", 
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(Modifier.height(48.dp))
            
            Text(
                text = "При регистрации генерируется уникальный RSA-2048 ключ.\nНикто, кроме вас, не имеет к нему доступа.",
                fontSize = 10.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )
            
            // Добавляем небольшой отступ снизу для безопасного скролла
            Spacer(Modifier.height(24.dp))
        }
    }
}
