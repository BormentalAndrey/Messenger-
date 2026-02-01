package com.kakdela.p2p.ui.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Модель данных для шага обучения
 */
data class OnboardingStep(
    val title: String,
    val description: String,
    val icon: String,
    val detail: String
)

val onboardingSteps = listOf(
    OnboardingStep(
        title = "P2P Связь",
        description = "Прямое соединение между устройствами.",
        icon = "🌐",
        detail = "В приложении «Как дела?» нет центральных серверов. Ваши сообщения передаются напрямую собеседнику. Это исключает слежку и цензуру на уровне провайдера."
    ),
    OnboardingStep(
        title = "RSA-2048 Шифрование",
        description = "Ваш телефон — ваш сейф.",
        icon = "🔒",
        detail = "При регистрации создается уникальная цифровая личность. Закрытый ключ хранится только в памяти вашего устройства. Никто, даже разработчики, не может прочитать вашу переписку."
    ),
    OnboardingStep(
        title = "Важная ответственность",
        description = "Потеря ключа = потеря доступа.",
        icon = "🔑",
        detail = "Поскольку серверов нет, мы не можем восстановить ваш аккаунт через SMS или Email. Если вы удалите приложение без бэкапа ключей, доступ к старым чатам будет утерян навсегда."
    )
)

/**
 * Основной экран обучения.
 * Реализован с учетом Android 15 (Edge-to-Edge) и различных диагоналей экранов.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { onboardingSteps.size })
    val scope = rememberCoroutineScope()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding() // Отступы для вырезов и системных панелей
        ) {
            // Кнопка пропуска (Skip)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = onFinished) {
                    Text("Пропустить", color = Color.Gray, fontSize = 14.sp)
                }
            }

            // Пейджер с контентом
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val step = onboardingSteps[page]
                
                // Внутренняя колонка со скроллом на случай крупного шрифта
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = step.icon,
                        fontSize = 72.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    Text(
                        text = step.title,
                        color = Color.Cyan,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        lineHeight = 34.sp
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        text = step.description,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Text(
                        text = step.detail,
                        color = Color.Gray,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                    
                    // Запасной спейсер снизу для скролла
                    Spacer(Modifier.height(32.dp))
                }
            }

            // Нижняя панель управления
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Индикаторы страниц (точки)
                Row(
                    Modifier
                        .height(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(onboardingSteps.size) { iteration ->
                        val color = if (pagerState.currentPage == iteration) Color.Cyan else Color.DarkGray
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(color)
                                .size(8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Динамическая кнопка
                Button(
                    onClick = {
                        if (pagerState.currentPage < onboardingSteps.size - 1) {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        } else {
                            onFinished()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp), // Гибкая высота для адаптации текста
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Cyan),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = if (pagerState.currentPage == onboardingSteps.size - 1) "Понятно, начать!" else "Далее",
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
