package com.kakdela.p2p.ui

import android.content.Context
import android.content.Intent
import android.net.VpnService as AndroidVpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kakdela.p2p.vpn.service.KakdelaVpnService
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val ctx = LocalContext.current
    val alpha = remember { Animatable(0f) }

    // Лаунчер для разрешения VPN
    val vpnLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        startVpn(ctx)
        onTimeout()
    }

    LaunchedEffect(Unit) {
        // Анимация текста
        alpha.animateTo(1f, animationSpec = tween(800))
        delay(1000)

        // Подготовка VPN
        val intent = AndroidVpnService.prepare(ctx)
        if (intent != null) {
            vpnLauncher.launch(intent)
        } else {
            startVpn(ctx)
            onTimeout()
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
                color = Color.Cyan,
                fontSize = 42.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.alpha(alpha.value)
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Маленький индикатор внизу, чтобы не было "зависшего" вида
            LinearProgressIndicator(
                modifier = Modifier.width(100.dp).alpha(alpha.value * 0.5f),
                color = Color.Cyan,
                trackColor = Color.DarkGray
            )
        }
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

