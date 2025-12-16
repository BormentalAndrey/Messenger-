package com.kakdela.p2p.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.kakdela.p2p.ui.*
import com.kakdela.p2p.ui.auth.EmailAuthScreen
import com.kakdela.p2p.ui.auth.PhoneAuthScreen
import com.kakdela.p2p.ui.auth.RegistrationChoiceScreen

object Routes {
    const val CHOICE = "choice"
    const val AUTH_EMAIL = "auth_email"
    const val AUTH_PHONE = "auth_phone"
    const val CHATS = "chats"
    const val CONTACTS = "contacts"
    const val ENTERTAINMENT = "entertainment"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{chatId}"
}

@Composable
fun NavGraph(navController: NavHostController) {

    val currentUser = Firebase.auth.currentUser
    val startDestination = if (currentUser == null) Routes.CHOICE else Routes.CHATS

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in listOf(
        Routes.CHATS,
        Routes.CONTACTS,
        Routes.ENTERTAINMENT,
        Routes.SETTINGS
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = Color.Black) {

                    NavigationBarItem(
                        selected = currentRoute == Routes.CHATS,
                        onClick = {
                            navController.navigate(Routes.CHATS) {
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Чаты"
                            )
                        },
                        label = { Text("Чаты") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.CONTACTS,
                        onClick = {
                            navController.navigate(Routes.CONTACTS) {
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = "Контакты"
                            )
                        },
                        label = { Text("Контакты") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.ENTERTAINMENT,
                        onClick = {
                            navController.navigate(Routes.ENTERTAINMENT) {
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Развлечения"
                            )
                        },
                        label = { Text("Развлечения") }
                    )

                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = {
                            navController.navigate(Routes.SETTINGS) {
                                launchSingleTop = true
                            }
                        },
                        icon = {
                            Text("=", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        },
                        label = { Text("Настройки") }
                    )
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {

            composable(Routes.CHOICE) {
                RegistrationChoiceScreen(
                    onEmail = {
                        navController.navigate(Routes.AUTH_EMAIL)
                    },
                    onPhone = {
                        navController.navigate(Routes.AUTH_PHONE)
                    }
                )
            }

            composable(Routes.AUTH_EMAIL) {
                EmailAuthScreen(
                    navController = navController,
                    onAuthSuccess = {
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.CHOICE) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.AUTH_PHONE) {
                PhoneAuthScreen(
                    navController = navController,
                    onSuccess = {
                        navController.navigate(Routes.CHATS) {
                            popUpTo(Routes.CHOICE) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.CHATS) {
                ChatsListScreen(navController)
            }

            composable(Routes.CONTACTS) {
                ContactsScreen(navController)
            }

            composable(Routes.ENTERTAINMENT) {
                EntertainmentScreen(navController)
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(navController)
            }

            composable(Routes.CHAT) { backStackEntry ->
                val chatId =
                    backStackEntry.arguments?.getString("chatId") ?: "global"
                val currentUserId =
                    Firebase.auth.currentUser?.uid ?: ""

                ChatScreen(
                    chatId = chatId,
                    currentUserId = currentUserId
                )
            }
        }
    }
}
