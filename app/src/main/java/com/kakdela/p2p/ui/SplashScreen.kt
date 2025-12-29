package com.kakdela.p2p.ui

import android.content.Context
import android.content.Intent
import android.net.VpnService as AndroidVpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import kotlinx.coroutines.delay
import com.kakdela.p2p.vpn.service.KakdelaVpnService

@Composable
fun SplashScreen(navController: NavHostController) {
    val ctx = LocalContext.current

    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startVpn(ctx)
        goNext(navController)
    }

    LaunchedEffect(Unit) {
        delay(900)
        val intent = AndroidVpnService.prepare(ctx)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            startVpn(ctx)
            goNext(navController)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

private fun startVpn(ctx: Context) {
    val intent = Intent(ctx, KakdelaVpnService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        ctx.startForegroundService(intent)
    } else {
        ctx.startService(intent)
    }
}

private fun goNext(nav: NavHostController) {
    nav.navigate("choice") { popUpTo(0) }
}

