package com.kakdela.p2p.game

import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.mystery_of_orient_express.game.MysteryOfOrientExpress

class JewelsBlastActivity : AndroidApplication() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val config = AndroidApplicationConfiguration().apply {
            useAccelerometer = false
            useCompass = false
            useGyroscope = false
            useImmersiveMode = true
            disableAudio = false
        }

        initialize(MysteryOfOrientExpress(), config)
    }
}
