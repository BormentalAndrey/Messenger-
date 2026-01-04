package com.kakdela.p2p.ui

import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.kakdela.p2p.game.JewelsBlastActivity

@Composable
fun JewelsBlastScreen() {
    val context = LocalContext.current

    Button(
        onClick = {
            context.startActivity(
                Intent(context, JewelsBlastActivity::class.java)
            )
        }
    ) {
        Text("Играть")
    }
}
