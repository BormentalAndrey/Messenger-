package com.kakdela.p2p.ui

import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.kakdela.p2p.ui.navigation.Routes

enum class DealType {
    WEB,
    CALCULATOR
}

data class DealItem(
    val id: String,
    val title: String,
    val description: String,
    val type: DealType,
    val url: String? = null
) {
    val iconLetter: String get() = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
}

private val dealItems = listOf(
    DealItem(
        id = "calculator",
        title = "Калькулятор",
        description = "Быстрые расчёты: скидки, бюджет, конвертер",
        type = DealType.CALCULATOR
    ),
    DealItem(
        id = "gosuslugi",
        title = "Госуслуги",
        description = "Государственные услуги РФ",
        type = DealType.WEB,
        url = "https://www.gosuslugi.ru"
    ),
    DealItem(
        id = "ozon",
        title = "Ozon",
        description = "Интернет-магазин и доставка",
        type = DealType.WEB,
        url = "https://www.ozon.ru"
    ),
    DealItem(
        id = "wb",
        title = "Wildberries",
        description = "Маркетплейс товаров",
        type = DealType.WEB,
        url = "https://www.wildberries.ru"
    ),
    DealItem(
        id = "drom",
        title = "Drom.ru",
        description = "Автомобили новые и с пробегом",
        type = DealType.WEB,
        url = "https://www.drom.ru"
    ),
    DealItem(
        id = "rbc",
        title = "РБК",
        description = "Новости экономики и бизнеса",
        type = DealType.WEB,
        url = "https://www.rbc.ru"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsScreen(navController: NavHostController) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Дела",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(paddingValues)
        ) {
            items(dealItems) { item ->
                DealListItem(item = item, navController = navController, context = context)
            }
        }
    }
}

@Composable
fun DealListItem(
    item: DealItem,
    navController: NavHostController,
    context: android.content.Context
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when (item.type) {
                    DealType.CALCULATOR -> navController.navigate(Routes.CALCULATOR)
                    DealType.WEB -> {
                        item.url?.let { url ->
                            val intent = CustomTabsIntent.Builder()
                                .setToolbarColor(MaterialTheme.colorScheme.primary.toArgb())
                                .setShowTitle(true)
                                .build()
                            intent.launchUrl(context, url.toUri())
                        }
                    }
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = item.iconLetter,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = item.description,
                color = Color.Gray,
                fontSize = 14.sp,
                maxLines = 1
            )
        }

        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Открыть",
            tint = Color.Gray,
            modifier = Modifier.size(20.dp)
        )
    }

    HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
}
