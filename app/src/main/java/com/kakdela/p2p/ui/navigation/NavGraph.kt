package com.kakdela.p2p.ui.navigation

import android.content.Context
import android.net.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.ui.chat.AiChatScreen
import com.kakdela.p2p.ui.player.MusicPlayerScreen
import com.kakdela.p2p.viewmodel.ChatViewModel
import com.kakdela.p2p.viewmodel.ChatViewModelFactory

/**
 * Объект с константами маршрутов. 
 * Убедитесь, что "text_editor" совпадает с тем, что вы вызываете в navController.navigate()
 */
object Routes {
    const val SPLASH = "splash"
    const val CHOICE = "choice"
    const val AUTH_EMAIL = "auth_email"
    const val AUTH_PHONE = "auth_phone"
    const val CHATS = "chats"
    const val CONTACTS = "contacts"
    const val DEALS = "deals"
    const val ENTERTAINMENT = "entertainment"
    const val SETTINGS = "settings"
    const val MUSIC = "music"
    const val CALCULATOR = "calculator"
    const val TIC_TAC_TOE = "tic_tac_toe"
    const val CHESS = "chess"
    const val PACMAN = "pacman"
    const val JEWELS = "jewels"
    const val SUDOKU = "sudoku"
    const val AI_CHAT = "ai_chat"
    const val TEXT_EDITOR = "text_editor" // Добавленный маршрут
}

@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val connected = remember {
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        mutableStateOf(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }
    DisposableEffect(cm) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { connected.value = true }
            override fun onLost(network: Network) { connected.value = false }
        }
        cm.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            cb
        )
        onDispose { cm.unregisterNetworkCallback(cb) }
    }
    return connected
}

@Composable
fun NoInternetScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = R.drawable.no_internet_neon),
                contentDescription = "Нет сети",
                modifier = Modifier.size(200.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "СОЕДИНЕНИЕ ПОТЕРЯНО",
                color = Color.Cyan,
                fontSize = 18.sp,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = "Эта функция требует подключения к сети",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    identityRepository: IdentityRepository
) {
    val isOnline by rememberIsOnline()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.CHATS, Routes.DEALS, Routes.ENTERTAINMENT, Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                    val items = listOf(
                        Triple(Routes.CHATS, Icons.Outlined.ChatBubbleOutline, "Чаты"),
                        Triple(Routes.DEALS, Icons.Filled.Checklist, "Дела"),
                        Triple(Routes.ENTERTAINMENT, Icons.Outlined.PlayCircleOutline, "Досуг"),
                        Triple(Routes.SETTINGS, Icons.Filled.Settings, "Опции")
                    )
                    items.forEach { (route, icon, label) ->
                        NavigationBarItem(
                            selected = currentRoute == route,
                            onClick = {
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        restoreState = true
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Cyan,
                                unselectedIconColor = Color.Gray,
                                indicatorColor = Color(0xFF1A1A1A),
                                selectedTextColor = Color.Cyan
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        // NavHost теперь работает всегда, независимо от интернета
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier
                .padding(paddingValues)
                .background(Color.Black)
        ) {
            composable(Routes.SPLASH) {
                SplashScreen {
                    val nextRoute = if (identityRepository.getMyId().isNotEmpty()) Routes.CHATS else Routes.CHOICE
                    navController.navigate(nextRoute) { popUpTo(Routes.SPLASH) { inclusive = true } }
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
                    navController.navigate(Routes.CHATS) { popUpTo(Routes.CHOICE) { inclusive = true } }
                }
            }

            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen(identityRepository) {
                    navController.navigate(Routes.CHATS) { popUpTo(Routes.CHOICE) { inclusive = true } }
                }
            }

            composable(Routes.CHATS) { 
                ChatsListScreen(navController, identityRepository) 
            }

            composable(Routes.CONTACTS) {
                ContactsScreen(
                    identityRepository = identityRepository,
                    onContactClick = { contact ->
                        val chatId = contact.publicKey ?: contact.phoneNumber
                        navController.navigate("chat/$chatId")
                    }
                )
            }

            composable(
                route = "chat/{chatId}",
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable
                val vm: ChatViewModel = viewModel(factory = ChatViewModelFactory(identityRepository))

                LaunchedEffect(chatId) { vm.initChat(chatId, identityRepository.getMyId()) }

                ChatScreen(
                    chatPartnerId = chatId,
                    messages = vm.messages.collectAsState().value,
                    identityRepository = identityRepository,
                    onSendMessage = vm::sendMessage,
                    onSendFile = vm::sendFile,
                    onSendAudio = vm::sendAudio,
                    onScheduleMessage = vm::scheduleMessage,
                    onBack = { navController.popBackStack() }
                )
            }

            // --- ОСНОВНЫЕ ОФЛАЙН ЭКРАНЫ ---
            composable(Routes.DEALS) { DealsScreen(navController) }
            composable(Routes.ENTERTAINMENT) { EntertainmentScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
            composable(Routes.MUSIC) { MusicPlayerScreen() }
            composable(Routes.CALCULATOR) { CalculatorScreen() }
            composable(Routes.TIC_TAC_TOE) { TicTacToeScreen() }
            composable(Routes.CHESS) { ChessScreen() }
            composable(Routes.PACMAN) { PacmanScreen() }
            composable(Routes.JEWELS) { JewelsBlastScreen() }
            composable(Routes.SUDOKU) { SudokuScreen() }
            
            // Исправление: Регистрация Текстового Редактора
            composable(Routes.TEXT_EDITOR) { 
                TextEditorScreen(navController = navController) 
            }

            // --- ЭКРАНЫ С ПРОВЕРКОЙ ИНТЕРНЕТА ---
            composable(
                route = "webview/{url}/{title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { e ->
                val url = e.arguments?.getString("url").orEmpty()
                val title = e.arguments?.getString("title").orEmpty()
                
                // Заглушка только для WebView
                if (isOnline) {
                    WebViewScreen(url, title, navController)
                } else {
                    NoInternetScreen()
                }
            }

            composable(Routes.AI_CHAT) {
                // AI чат обычно тоже требует интернет
                if (isOnline) AiChatScreen() else NoInternetScreen()
            }
        }
    }
}
