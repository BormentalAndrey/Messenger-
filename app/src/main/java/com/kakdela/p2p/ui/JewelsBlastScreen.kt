package com.kakdela.p2p.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kakdela.p2p.game.JewelsBlastActivity

@Composable
fun JewelsBlastScreen() {
    val context = LocalContext.current

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Button(
            modifier = Modifier
                .width(220.dp)
                .height(56.dp),
            onClick = {
                context.startActivity(
                    Intent(context, JewelsBlastActivity::class.java)
                )
            }
        ) {
            Text(
                text = "🎮 Играть",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
