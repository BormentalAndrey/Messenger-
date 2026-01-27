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
import androidx.compose.material.icons.filled.Folder
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
import com.kakdela.p2p.ui.terminal.TerminalActivity
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Типы пунктов
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
            DealType.TOOL -> {
                if (id == "file_manager") Icons.Filled.Folder
                else Icons.Filled.Edit
            }
            DealType.ACTIVITY -> {
                when (id) {
                    "browser" -> Icons.Filled.Public
                    "terminal" -> Icons.Filled.Edit
                    else -> Icons.Filled.ShoppingBag
                }
            }
            else -> Icons.Filled.ShoppingBag
        }
}

private val dealItems = listOf(
    // ===== Activity =====
    DealItem(
        id = "browser",
        title = "P2P Браузер",
        description = "Анонимный сёрфинг",
        type = DealType.ACTIVITY
    ),
    DealItem(
        id = "terminal",
        title = "Терминал",
        description = "Полноценный Termux",
        type = DealType.ACTIVITY
    ),

    // ===== Инструменты =====
    DealItem(
        id = "file_manager",
        title = "Файловый менеджер",
        description = "Управление файлами",
        type = DealType.TOOL
    ),
    DealItem(
        id = "calculator",
        title = "Калькулятор",
        description = "Быстрые расчёты",
        type = DealType.CALCULATOR
    ),
    DealItem(
        id = "text_editor",
        title = "Текстовый редактор",
        description = "TXT, DOCX, PDF (чтение)",
        type = DealType.TOOL
    ),

    // ===== WEB =====
    DealItem(
        id = "gosuslugi",
        title = "Госуслуги",
        description = "Госуслуги РФ",
        type = DealType.WEB,
        url = "https://www.gosuslugi.ru"
    ),
    DealItem(
        id = "ozon",
        title = "Ozon",
        description = "Маркетплейс",
        type = DealType.WEB,
        url = "https://www.ozon.ru"
    ),
    DealItem(
        id = "wb",
        title = "Wildberries",
        description = "Маркетплейс",
        type = DealType.WEB,
        url = "https://www.wildberries.ru"
    ),
    DealItem(
        id = "drom",
        title = "Drom.ru",
        description = "Автомобили",
        type = DealType.WEB,
        url = "https://www.drom.ru"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(navController: NavHostController) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Дела",
                        fontWeight = FontWeight.Black,
                        color = Color.Magenta,
                        letterSpacing = 2.sp
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black
                )
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
    val context = LocalContext.current

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
                DealType.ACTIVITY -> {
                    val intent = when (item.id) {
                        "browser" -> Intent(context, BrowserActivity::class.java)
                        "terminal" -> Intent(context, TerminalActivity::class.java)
                        else -> null
                    }
                    intent?.let { context.startActivity(it) }
                }

                DealType.CALCULATOR ->
                    navController.navigate(Routes.CALCULATOR)

                DealType.TOOL ->
                    if (item.id == "text_editor") {
                        navController.navigate(Routes.TEXT_EDITOR)
                    } else if (item.id == "file_manager") {
                        navController.navigate(Routes.FILE_MANAGER)
                    }

                DealType.WEB -> item.url?.let {
                    val encoded = URLEncoder.encode(it, StandardCharsets.UTF_8.toString())
                    navController.navigate("webview/$encoded/${item.title}")
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
            Icon(
                imageVector = item.iconVector,
                contentDescription = null,
                tint = neonColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    item.description,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )
            }
            Icon(
                imageVector = Icons.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = neonColor.copy(alpha = 0.5f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
