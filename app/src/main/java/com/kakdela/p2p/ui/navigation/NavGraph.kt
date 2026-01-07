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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.kakdela.p2p.R
import com.kakdela.p2p.data.IdentityRepository
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.*
import com.kakdela.p2p.ui.player.MusicPlayerScreen
import com.kakdela.p2p.viewmodel.ChatViewModel
import com.kakdela.p2p.viewmodel.ChatViewModelFactory

@Composable
fun rememberIsOnline(): State<Boolean> {
    val context = LocalContext.current
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val connected = remember {
        val net = cm.activeNetwork
        val caps = cm.getNetworkCapabilities(net)
        mutableStateOf(caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    }

    DisposableEffect(Unit) {
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
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.no_internet_neon),
                null,
                Modifier.size(200.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(16.dp))
            Text("Требуется подключение к сети", color = Color.White)
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
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    val showBottomBar = route in listOf(
        Routes.CHATS,
        Routes.DEALS,
        Routes.ENTERTAINMENT,
        Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color(0xFF0A0A0A)) {
                    listOf(
                        Triple(Routes.CHATS, Icons.Outlined.ChatBubbleOutline, "Чаты"),
                        Triple(Routes.DEALS, Icons.Filled.Checklist, "Дела"),
                        Triple(Routes.ENTERTAINMENT, Icons.Outlined.PlayCircleOutline, "Развлечения"),
                        Triple(Routes.SETTINGS, Icons.Filled.Settings, "Настройки")
                    ).forEach { (r, icon, label) ->
                        NavigationBarItem(
                            selected = route == r,
                            onClick = {
                                if (route != r) {
                                    navController.navigate(r) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        restoreState = true
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Icon(icon, null) },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }
    ) { padding ->

        NavHost(
            navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.padding(padding).background(Color.Black)
        ) {

            composable(Routes.SPLASH) {
                SplashScreen {
                    navController.navigate(
                        if (identityRepository.getMyId().isNotEmpty()) Routes.CHATS else Routes.CHOICE
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
                EmailAuthScreen {
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

            composable(Routes.CHATS) {
                ChatsListScreen(navController, identityRepository)
            }

            composable(Routes.CONTACTS) {
                ContactsScreen { id -> navController.navigate("chat/$id") }
            }

            composable("chat/{chatId}") { entry ->
                val chatId = entry.arguments?.getString("chatId") ?: return@composable

                val vm: ChatViewModel = viewModel(
                    factory = ChatViewModelFactory(identityRepository)
                )

                LaunchedEffect(chatId) {
                    vm.initChat(chatId, identityRepository.getMyId())
                }

                ChatScreen(
                    chatPartnerId = chatId,
                    messages = vm.messages.collectAsState().value,
                    identityRepository = identityRepository,
                    onSendMessage = vm::sendMessage,
                    onSendFile = vm::sendFile,
                    onSendAudio = vm::sendAudio,
                    onScheduleMessage = vm::scheduleMessage
                )
            }

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

            composable(
                "webview/{url}/{title}",
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType }
                )
            ) { e ->
                if (isOnline) {
                    WebViewScreen(
                        e.arguments?.getString("url").orEmpty(),
                        e.arguments?.getString("title").orEmpty(),
                        navController
                    )
                } else NoInternetScreen()
            }
        }
    }
}
