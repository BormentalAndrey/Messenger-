package com.kakdela.p2p.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.kakdela.p2p.vpn.service.VpnService as AppVpnService
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(navController: NavHostController) {
    val context = LocalContext.current

    // Лаунчер для системного окна разрешения VPN
    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        // После того как пользователь нажал "ОК" (или отмену), 
        // пробуем запустить сервис и идем дальше
        startVpnLogic(context)
        proceedToNextScreen(navController)
    }

    LaunchedEffect(Unit) {
        delay(1200)

        // 1. Проверяем, нужно ли запрашивать разрешение на VPN
        val intent = VpnService.prepare(context)
        if (intent != null) {
            // Если intent не null, значит разрешение еще не дано (первый запуск)
            vpnLauncher.launch(intent)
        } else {
            // Разрешение уже есть, запускаем VPN и переходим
            startVpnLogic(context)
            proceedToNextScreen(navController)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Как дела?",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * Логика перехода на следующий экран
 */
private fun proceedToNextScreen(navController: NavHostController) {
    val user = FirebaseAuth.getInstance().currentUser
    val target = if (user != null) "chats" else "choice"

    navController.navigate(target) {
        popUpTo(0)
    }
}

/**
 * Запуск VPN сервиса (Cloudflare WARP)
 */
private fun startVpnLogic(context: Context) {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = cm.activeNetwork
    val caps = cm.getNetworkCapabilities(activeNetwork)

    // Если сторонний VPN уже запущен, наш не запускаем, чтобы не конфликтовать
    val isExternalVpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    
    if (!isExternalVpnActive) {
        val intent = Intent(context, AppVpnService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
