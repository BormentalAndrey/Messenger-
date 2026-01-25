package com.kakdela.p2p.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.kakdela.p2p.ui.browser.BrowserActivity
import com.kakdela.p2p.ui.navigation.Routes
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Добавлен тип ACTIVITY для внешних экранов
enum class DealType { WEB, CALCULATOR, TOOL, ACTIVITY }

data class DealItem(
    val id: String,
    val title: String,
    val description: String,
    val type: DealType,
    val url: String? = null
) {
    val iconVector: ImageVector
        get() = when (type) {
            DealType.CALCULATOR -> Icons.Filled.Calculate
            DealType.TOOL -> Icons.Filled.Edit
            DealType.ACTIVITY -> {
                when (id) {
                    "browser" -> Icons.Filled.Public
                    else -> Icons.Filled.ShoppingBag
                }
            }
            else -> Icons.Filled.ShoppingBag
        }
}

private val dealItems = listOf(
    // Новые пункты меню для Активностей
    DealItem("browser", "P2P Браузер", "Анонимный сёрфинг", DealType.ACTIVITY),
    
    // Существующие инструменты
    DealItem("calculator", "Калькулятор", "Быстрые расчёты", DealType.CALCULATOR),
    DealItem("text_editor", "Текстовый редактор", "TXT, DOCX, PDF (чтение)", DealType.TOOL),
    
    // Веб-сервисы
    DealItem("gosuslugi", "Госуслуги", "Госуслуги РФ", DealType.WEB, "https://www.gosuslugi.ru"),
    DealItem("ozon", "Ozon", "Маркетплейс", DealType.WEB, "https://www.ozon.ru"),
    DealItem("wb", "Wildberries", "Маркетплейс", DealType.WEB, "https://www.wildberries.ru"),
    DealItem("drom", "Drom.ru", "Автомобили", DealType.WEB, "https://www.drom.ru")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        "Дела", 
                        fontWeight = FontWeight.Black, 
                        color = Color.Magenta, 
                        letterSpacing = 2.sp
                    ) 
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Black)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(dealItems) { item ->
                DealNeonItem(item, navController)
            }
        }
    }
}

@Composable
fun DealNeonItem(item: DealItem, navController: NavHostController) {
    val neonColor = Color.Magenta
    val context = LocalContext.current // Получаем контекст для запуска Activity

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(6.dp, spotColor = neonColor),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, neonColor.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF120012)),
        onClick = {
            when (item.type) {
                // Логика запуска Activity
                DealType.ACTIVITY -> {
                    val intent = when (item.id) {
                        "browser" -> Intent(context, BrowserActivity::class.java)
                        else -> null
                    }
                    intent?.let { context.startActivity(it) }
                }
                
                // Логика навигации Compose
                DealType.CALCULATOR -> navController.navigate(Routes.CALCULATOR)
                DealType.TOOL -> if (item.id == "text_editor") navController.navigate(Routes.TEXT_EDITOR)
                DealType.WEB -> item.url?.let {
                    val encoded = URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
                    navController.navigate("webview/${encoded}/${item.title}")
                }
            }
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(item.iconVector, null, tint = neonColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(item.description, color = Color.White.copy(0.6f), fontSize = 12.sp)
            }
            Icon(
                Icons.Filled.ArrowForwardIos, 
                null, 
                tint = neonColor.copy(0.5f), 
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
