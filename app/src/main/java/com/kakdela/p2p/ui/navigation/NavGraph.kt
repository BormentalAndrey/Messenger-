package com.kakdela.p2p.ui.navigation

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.ui.chat.AiChatScreen
import com.kakdela.p2p.ui.chat.ChatScreen
import com.kakdela.p2p.ui.player.MusicPlayerScreen
// ИСПРАВЛЕНО: Импортируем именно функцию Slots1Screen
import com.kakdela.p2p.ui.slots.Slots1Screen
import com.kakdela.p2p.ui.ChatViewModel
import com.kakdela.p2p.viewmodel.ChatViewModelFactory

@Composable
fun NavGraph(
    navController: NavHostController,
    identityRepository: IdentityRepository,
    startDestination: String
) {
    val context = LocalContext.current
    val isOnline by rememberIsOnline()
    
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val messageRepository = identityRepository.messageRepository

    val showBottomBar = currentRoute in listOf(
        Routes.CHATS,
        Routes.DEALS,
        Routes.ENTERTAINMENT,
        Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomBar(currentRoute, navController)
            }
        },
        containerColor = Color.Black
    ) { paddingValues ->

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(paddingValues)
                .background(Color.Black)
        ) {

            /* ================= AUTH ================= */

            composable(Routes.SPLASH) {
                SplashScreen {
                    val prefs = context.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                    val isLoggedIn = prefs.getBoolean("is_logged_in", false)

                    navController.navigate(
                        if (isLoggedIn) Routes.CHATS else Routes.CHOICE
                    ) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }

            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onPhone = { navController.navigate(Routes.AUTH_PHONE) },
                    onEmailOnly = { navController.navigate(Routes.AUTH_EMAIL) }
                )
            }

            composable(Routes.AUTH_EMAIL) {
                EmailAuthScreen(identityRepository) {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CHOICE) { inclusive = true }
                    }
                }
            }

            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen {
                    navController.navigate(Routes.CHATS) {
                        popUpTo(Routes.CHOICE) { inclusive = true }
                    }
                }
            }

            /* ================= MAIN ================= */

            composable(Routes.CHATS) {
                ChatsListScreen(navController = navController)
            }

            composable(Routes.CONTACTS) {
                ContactsScreen(
                    identityRepository = identityRepository,
                    onContactClick = { contact ->
                        contact.userHash?.let { hash ->
                            navController.navigate(Routes.buildChatRoute(hash))
                        } ?: run {
                            Toast.makeText(
                                context,
                                "Пользователь ещё не в P2P-сети. Пригласите его.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                )
            }

            /* ================= CHAT ================= */

            composable(
                route = Routes.CHAT_DIRECT,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType }
                )
            ) { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: ""
                val app = context.applicationContext as Application

                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(
                        identityRepository,
                        messageRepository,
                        app
                    )
                )

                LaunchedEffect(chatId) {
                    vm.initChat(chatId)
                }

                val messages by vm.messages.collectAsState()

                ChatScreen(
                    chatPartnerId = chatId,
                    messages = messages,
                    identityRepository = identityRepository,
                    onSendMessage = { text -> vm.sendMessage(text) },
                    onSendFile = { uri, fileName -> vm.sendFile(uri, fileName) },
                    onSendAudio = { uri, duration ->
                        val audioFileName = "audio_msg_${System.currentTimeMillis()}_${duration}s.mp3"
                        vm.sendFile(uri, audioFileName)
                    },
                    onScheduleMessage = { text, time -> vm.scheduleMessage(text, time) },
                    onBack = { navController.popBackStack() }
                )
            }

            /* ================= SECTIONS ================= */

            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    navController = navController,
                    identityRepository = identityRepository
                )
            }

            composable(Routes.MUSIC) { MusicPlayerScreen() }

            /* ================= WEBVIEW ================= */

            composable(
                route = "webview/{url}/{title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { entry ->
                val url = entry.arguments?.getString("url") ?: ""
                val title = entry.arguments?.getString("title") ?: ""

                if (isOnline) {
                    WebViewScreen(
                        url = url,
                        title = title,
                        navController = navController
                    )
                } else {
                    NoInternetScreen {
                        navController.popBackStack()
                    }
                }
            }

            /* ================= TOOLS & GAMES ================= */

            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.TEXT_EDITOR) { TextEditorScreen(navController) }
            
            // ИСПРАВЛЕНО: Теперь используем правильное имя функции Slots1Screen
            composable(Routes.SLOTS_1) { Slots1Screen(navController) }
            
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.SUDOKU) { SudokuScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }

            composable(Routes.AI_CHAT) {
                if (isOnline) {
                    AiChatScreen()
                } else {
                    NoInternetScreen {
                        navController.popBackStack()
                    }
                }
            }
        }
    }
}

/* ================= UI HELPERS ================= */

@Composable
private fun AppBottomBar(
    currentRoute: String?,
    navController: NavHostController
) {
    NavigationBar(
        containerColor = Color(0xFF010101),
        tonalElevation = 0.dp
    ) {
        val items = listOf(
            Triple(Routes.CHATS, Icons.Outlined.ChatBubbleOutline, "Чаты"),
            Triple(Routes.DEALS, Icons.Filled.Checklist, "Дела"),
            Triple(Routes.ENTERTAINMENT, Icons.Outlined.PlayCircleOutline, "Досуг"),
            Triple(Routes.SETTINGS, Icons.Filled.Settings, "Опции")
        )

        items.forEach { (route, icon, label) ->
            val selected = currentRoute == route

            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected) Color(0xFF00FFFF) else Color.Gray
                    )
                },
                label = {
                    Text(
                        label,
                        fontSize = 10.sp,
                        color = if (selected) Color(0xFF00FFFF) else Color.Gray
                    )
                }
            )
        }
    }
}

@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(true) }

    DisposableEffect(context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { state.value = true }
            override fun onLost(network: Network) { state.value = false }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        try {
            cm.registerNetworkCallback(request, callback)
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)
            state.value = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            state.value = true 
        }
        onDispose {
            try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
        }
    }
    return state
}

@Composable
fun NoInternetScreen(onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                tint = Color(0xFF00FFFF).copy(alpha = 0.6f),
                modifier = Modifier.size(80.dp)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Нет соединения",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(32.dp))
            OutlinedButton(
                onClick = onBack,
                border = BorderStroke(1.dp, Color(0xFF00FFFF)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FFFF))
            ) {
                Text("ВЕРНУТЬСЯ", fontWeight = FontWeight.Bold)
            }
        }
    }
}
