package com.kakdela.p2p.ui.player

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AlphaAnimation
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kakdela.p2p.R

class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var btnPlayPause: AppCompatImageButton
    private lateinit var btnNext: AppCompatImageButton
    private lateinit var btnPrev: AppCompatImageButton
    private lateinit var btnFullscreen: AppCompatImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var videoTitle: TextView
    private lateinit var controlsRoot: View

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private var isFullscreen = false

    private val playlist = listOf(
        VideoModel("Big Buck Bunny", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
        VideoModel("Elephants Dream", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        playerView = findViewById(R.id.player_view)
        controlsRoot = findViewById(R.id.controls_root)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnPrev = findViewById(R.id.btn_prev)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        seekBar = findViewById(R.id.seek_bar)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        videoTitle = findViewById(R.id.video_title)

        initializePlayer()
        setupControls()
    }

    private fun initializePlayer() {
        // Включаем поддержку всех кодеков (включая программные)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(this, renderersFactory).build()
        playerView.player = player

        playlist.forEach { video ->
            player.addMediaItem(MediaItem.fromUri(video.url))
        }

        player.prepare()
        player.playWhenReady = true

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                videoTitle.text = playlist.getOrNull(player.currentMediaItemIndex)?.title ?: ""
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(
                    if (isPlaying) androidx.media3.ui.R.drawable.exo_styled_controls_pause
                    else androidx.media3.ui.R.drawable.exo_styled_controls_play
                )
                if (isPlaying) startSeekBarUpdate()
            }
        })
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener { if (player.isPlaying) player.pause() else player.play() }
        btnNext.setOnClickListener { if (player.hasNextMediaItem()) player.seekToNextMediaItem() }
        btnPrev.setOnClickListener { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem() }
        btnFullscreen.setOnClickListener { toggleFullscreen() }
        
        playerView.setOnClickListener { 
            if (controlsRoot.visibility == View.VISIBLE) hideControls() else showControls() 
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) player.seekTo((p.toLong() * player.duration) / 1000)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    // Вставьте остальные методы (formatTime, toggleFullscreen, showControls и т.д.) из предыдущего ответа
    private fun showControls() {
        controlsRoot.visibility = View.VISIBLE
        hideHandler.removeCallbacks(hideRunnable)
        hideHandler.postDelayed(hideRunnable, 5000)
    }

    private fun hideControls() {
        controlsRoot.visibility = View.GONE
    }

    private fun startSeekBarUpdate() {
        hideHandler.post(object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    val progress = if (player.duration > 0) (player.currentPosition * 1000 / player.duration).toInt() else 0
                    seekBar.progress = progress
                    currentTime.text = formatTime(player.currentPosition)
                    totalTime.text = formatTime(player.duration)
                    hideHandler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60)
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        requestedOrientation = if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE 
                               else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
    }
}

