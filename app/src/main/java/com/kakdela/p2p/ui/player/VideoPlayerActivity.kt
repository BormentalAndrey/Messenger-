package com.kakdela.p2p.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AlphaAnimation
import androidx.activity.ComponentActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView

class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    private lateinit var controlsRoot: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var videoTitle: TextView

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }

    private var isFullscreen = false

    // Тестовое видео (можно заменить или передавать через Intent)
    private val videoUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.player_view)
        controlsRoot = findViewById(R.id.controls_root)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        seekBar = findViewById(R.id.seek_bar)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        videoTitle = findViewById(R.id.video_title)

        initializePlayer()
        setupControls()
        showControls()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player

        val mediaItem = MediaItem.fromUri(videoUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_READY) {
                    totalTime.text = formatTime(player.duration)
                    updateSeekBar()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(
                    if (isPlaying) androidx.media3.ui.R.drawable.exo_icon_pause
                    else androidx.media3.ui.R.drawable.exo_icon_play
                )
                if (isPlaying) startSeekBarUpdate()
            }
        })
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener {
            if (player.isPlaying) player.pause() else player.play()
        }

        btnFullscreen.setOnClickListener {
            toggleFullscreen()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && player.duration > 0) {
                    val position = progress.toLong() * player.duration / 1000
                    player.seekTo(position)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        playerView.setOnClickListener {
            if (controlsRoot.visibility == View.VISIBLE) hideControls() else showControls()
        }
    }

    private fun startSeekBarUpdate() {
        hideHandler.removeCallbacks(updateSeekBarRunnable)
        hideHandler.post(updateSeekBarRunnable)
    }

    private val updateSeekBarRunnable: Runnable = object : Runnable {
        override fun run() {
            if (player.duration > 0) {
                val progress = (player.currentPosition * 1000 / player.duration).toInt()
                seekBar.progress = progress.coerceIn(0, 1000)
                currentTime.text = formatTime(player.currentPosition)
            }
            hideHandler.postDelayed(this, 500)
        }
    }

    private fun showControls() {
        controlsRoot.visibility = View.VISIBLE
        val anim = AlphaAnimation(0f, 1f)
        anim.duration = 300
        controlsRoot.startAnimation(anim)

        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000)
    }

    private fun hideControls() {
        val anim = AlphaAnimation(1f, 0f)
        anim.duration = 300
        controlsRoot.startAnimation(anim)
        controlsRoot.visibility = View.GONE
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            hideSystemUi()
            playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
            btnFullscreen.setImageResource(androidx.media3.ui.R.drawable.exo_icon_fullscreen_exit)
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            showSystemUi()
            playerView.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            btnFullscreen.setImageResource(androidx.media3.ui.R.drawable.exo_icon_fullscreen_enter)
        }
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }
    }

    private fun showSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0) return "00:00"
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        hideHandler.removeCallbacksAndMessages(null)
    }
}
