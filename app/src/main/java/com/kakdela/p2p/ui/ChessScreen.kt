package com.kakdela.p2p.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChessScreen() {
    // Очень упрощённая версия для примера (полноценные шахматы — это большой код)
    // Можно использовать библиотеку chess.kt позже
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Шахматы", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black).padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Шахматы в разработке\n(Скоро добавим полноценную игру с ИИ)",
                color = Color.White,
                fontSize = 24.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
