package com.kakdela.p2p.ui.player

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Rational
import android.util.Size
import android.view.*
import android.view.animation.AlphaAnimation
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kakdela.p2p.R
import android.Manifest
import android.app.PictureInPictureParams
import android.content.ContentUris
import java.text.SimpleDateFormat
import java.util.*

data class VideoModel(
    val id: Long,
    val title: String,
    val uri: Uri
)

class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView

    // Controls
    private lateinit var controlsRoot: View
    private lateinit var topControls: View
    private lateinit var overlayContainer: ViewGroup
    private lateinit var centerIndicator: TextView
    private lateinit var lockIcon: ImageView

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var btnPlaylist: ImageButton
    private lateinit var btnSpeed: ImageButton
    private lateinit var btnLock: ImageButton
    private lateinit var btnPip: ImageButton

    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var videoTitle: TextView
    private lateinit var clockText: TextView
    private lateinit var batteryText: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val playlist = mutableListOf<VideoModel>()
    private var currentIndex = 0

    private var isFullscreen = false
    private var isLocked = false
    private var isUserSeeking = false

    // Неоновые цвета
    private val neonCyan = Color.parseColor("#00FFFF")
    private val neonPink = Color.parseColor("#FF00FF")
    private val neonPurple = Color.parseColor("#B026FF")
    private val neonGreen = Color.parseColor("#39FF14")

    // Скорости
    private val speeds = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    private var currentSpeedIndex = 2

    // Жесты
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchAction = 0 // 0=none, 1=seek, 2=brightness, 3=volume
    private var initialSeekPos = 0L
    private var initialVolume = 0
    private var initialBrightness = 0.5f

    private val SEEK_FULL_SWIPE_SECONDS = 60L

    private lateinit var audioManager: AudioManager

    private var batteryReceiver: BroadcastReceiver? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) loadVideos()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        window.setBackgroundDrawable(ColorDrawable(Color.BLACK))

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager

        bindViews()
        initPlayer()
        setupGestures()
        setupControls()
        applyNeonStyle()
        setupBatteryAndClock()
        checkPermission()
    }

    private fun bindViews() {
        playerView = findViewById(R.id.player_view)
        controlsRoot = findViewById(R.id.controls_root)
        topControls = findViewById(R.id.top_controls)
        overlayContainer = findViewById(R.id.overlay_container)
        centerIndicator = findViewById(R.id.center_indicator)
        lockIcon = findViewById(R.id.lock_icon)

        clockText = findViewById(R.id.clock_text)
        batteryText = findViewById(R.id.battery_text)

        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnPrev = findViewById(R.id.btn_prev)
        btnNext = findViewById(R.id.btn_next)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        btnPlaylist = findViewById(R.id.btn_playlist)
        btnSpeed = findViewById(R.id.btn_speed)
        btnLock = findViewById(R.id.btn_lock)
        btnPip = findViewById(R.id.btn_pip)

        seekBar = findViewById(R.id.seek_bar)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        videoTitle = findViewById(R.id.video_title)
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.useController = false

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    totalTime.text = formatTime(player.duration)
                    seekBar.max = player.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    startProgressUpdater()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                if (isPlaying) startHideTimer() else cancelHideTimer()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
                videoTitle.text = playlist.getOrNull(currentIndex)?.title ?: "Unknown"
            }
        })
    }

    private fun setupGestures() {
        playerView.setOnTouchListener { _, event ->
            if (isLocked) {
                // При locked тачи идут только на разблокировку (lockIcon clickable)
                false
            } else {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        touchStartY = event.y
                        touchAction = 0
                        initialSeekPos = player.currentPosition
                        initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        val lp = window.attributes
                        initialBrightness = if (lp.screenBrightness > 0) lp.screenBrightness else 0.5f
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (touchAction == 0) {
                            val deltaX = event.x - touchStartX
                            val deltaY = event.y - touchStartY
                            if (kotlin.math.abs(deltaX) > 60 && kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
                                touchAction = 1 // seek
                            } else if (kotlin.math.abs(deltaY) > 60 && kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)) {
                                touchAction = if (touchStartX < playerView.width / 2f) 2 else 3
                            }
                        }

                        when (touchAction) {
                            1 -> {
                                val deltaX = event.x - touchStartX
                                val seekAmount = (deltaX / playerView.width * SEEK_FULL_SWIPE_SECONDS * 1000).toLong()
                                val newPos = (initialSeekPos + seekAmount).coerceIn(0L, player.duration)
                                player.seekTo(newPos)
                                showCenterIndicator("\( {if (seekAmount > 0) "+" else "-"} \){formatTime(kotlin.math.abs(seekAmount))}", neonGreen)
                            }
                            2 -> {
                                val deltaY = touchStartY - event.y
                                val delta = deltaY / playerView.height
                                val newBright = (initialBrightness + delta).coerceIn(0.1f, 1.0f)
                                window.attributes = window.attributes.apply { screenBrightness = newBright }
                                showCenterIndicator("Brightness ${(newBright * 100).toInt()}%", neonCyan)
                            }
                            3 -> {
                                val deltaY = touchStartY - event.y
                                val delta = deltaY / playerView.height
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val newVol = (initialVolume + (delta * maxVol)).toInt().coerceIn(0, maxVol)
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                showCenterIndicator("Volume ${(newVol * 100 / maxVol).toInt()}%", neonPink)
                            }
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (touchAction != 0) {
                            hideCenterIndicatorDelayed()
                            showControlsTemporarily()
                        }
                        touchAction = 0
                    }
                }
                true
            }
        }

        // Разблокировка по тапу на lockIcon
        lockIcon.setOnClickListener { toggleLock() }
    }

    private fun showCenterIndicator(text: String, color: Int = Color.WHITE) {
        centerIndicator.text = text
        centerIndicator.setTextColor(color)
        centerIndicator.setShadowLayer(30f, 0f, 0f, neonPink)
        centerIndicator.visibility = View.VISIBLE
        handler.removeCallbacks(hideIndicatorRunnable)
        handler.postDelayed(hideIndicatorRunnable, 1500)
    }

    private val hideIndicatorRunnable = Runnable {
        centerIndicator.visibility = View.GONE
    }

    private fun hideCenterIndicatorDelayed() {
        handler.removeCallbacks(hideIndicatorRunnable)
        handler.postDelayed(hideIndicatorRunnable, 800)
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener { player.playWhenReady = !player.playWhenReady; showControlsTemporarily() }
        btnNext.setOnClickListener { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); showControlsTemporarily() }
        btnPrev.setOnClickListener { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); showControlsTemporarily() }
        btnFullscreen.setOnClickListener { toggleFullscreen(); showControlsTemporarily() }
        btnPlaylist.setOnClickListener { showPlaylist(); showControlsTemporarily() }

        btnSpeed.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speeds.size
            player.setPlaybackSpeed(speeds[currentSpeedIndex])
            Toast.makeText(this, "${speeds[currentSpeedIndex]}x", Toast.LENGTH_SHORT).show()
            showControlsTemporarily()
        }

        btnLock.setOnClickListener { toggleLock() }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            btnPip.visibility = View.VISIBLE
            btnPip.setOnClickListener { enterPictureInPicture() }
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(p0: SeekBar?) { isUserSeeking = true; cancelHideTimer() }
            override fun onStopTrackingTouch(p0: SeekBar?) { isUserSeeking = false; startHideTimer() }
            override fun onProgressChanged(p0: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    player.seekTo(progress.toLong())
                    currentTime.text = formatTime(progress.toLong())
                }
            }
        })
    }

    private fun toggleLock() {
        isLocked = !isLocked
        if (isLocked) {
            hideControls()
            lockIcon.visibility = View.VISIBLE
            btnLock.setImageResource(android.R.drawable.ic_lock_lock)
        } else {
            lockIcon.visibility = View.GONE
            btnLock.setImageResource(android.R.drawable.ic_lock_idle_lock)
            showControlsTemporarily()
        }
    }

    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ratio = if (player.videoSize.width > 0 && player.videoSize.height > 0) {
                Rational(player.videoSize.width, player.videoSize.height)
            } else {
                Rational(16, 9)
            }
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(ratio)
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && player.isPlaying) {
            enterPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        if (isInPictureInPictureMode) {
            hideControls()
            topControls.visibility = View.GONE
        } else {
            showControlsTemporarily()
            topControls.visibility = View.VISIBLE
        }
    }

    private fun applyNeonStyle() {
        videoTitle.setTextColor(neonPink)
        videoTitle.setShadowLayer(20f, 0f, 0f, neonPink)

        currentTime.setTextColor(Color.WHITE)
        currentTime.setShadowLayer(12f, 0f, 0f, neonCyan)
        totalTime.setTextColor(Color.WHITE)
        totalTime.setShadowLayer(12f, 0f, 0f, neonCyan)

        clockText.setTextColor(neonCyan)
        clockText.setShadowLayer(10f, 0f, 0f, neonCyan)
        batteryText.setTextColor(neonGreen)
        batteryText.setShadowLayer(10f, 0f, 0f, neonGreen)

        centerIndicator.setTextSize(48f)

        seekBar.progressTintList = android.content.res.ColorStateList.valueOf(neonCyan)
        seekBar.thumbTintList = android.content.res.ColorStateList.valueOf(neonPink)

        val tintCyan = android.content.res.ColorStateList.valueOf(neonCyan)
        val tintPink = android.content.res.ColorStateList.valueOf(neonPink)
        val tintPurple = android.content.res.ColorStateList.valueOf(neonPurple)
        val tintGreen = android.content.res.ColorStateList.valueOf(neonGreen)

        btnPlayPause.imageTintList = tintGreen
        btnPrev.imageTintList = tintCyan
        btnNext.imageTintList = tintCyan
        btnFullscreen.imageTintList = tintPurple
        btnPlaylist.imageTintList = tintPurple
        btnSpeed.imageTintList = tintPink
        btnLock.imageTintList = tintPurple
        btnPip.imageTintList = tintCyan

        controlsRoot.background = ColorDrawable(Color.parseColor("#B3000000"))
        topControls.background = ColorDrawable(Color.parseColor("#80000000"))
    }

    private fun showControls() {
        if (isLocked) return
        controlsRoot.visibility = View.VISIBLE
        topControls.visibility = View.VISIBLE
        AlphaAnimation(0f, 1f).apply { duration = 300 }.also { controlsRoot.startAnimation(it); topControls.startAnimation(it) }
        startHideTimer()
    }

    private fun hideControls() {
        AlphaAnimation(1f, 0f).apply { duration = 300 }.also {
            it.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    if (!isLocked) {
                        controlsRoot.visibility = View.GONE
                        topControls.visibility = View.GONE
                    }
                }
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
            })
            controlsRoot.startAnimation(it)
            topControls.startAnimation(it)
        }
    }

    private fun showControlsTemporarily() {
        showControls()
        startHideTimer()
    }

    private fun startHideTimer() {
        cancelHideTimer()
        if (player.isPlaying) handler.postDelayed({ hideControls() }, 4000)
    }

    private fun cancelHideTimer() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun startProgressUpdater() {
        handler.post(object : Runnable {
            override fun run() {
                if (player.isPlaying && !isUserSeeking) {
                    val pos = player.currentPosition
                    seekBar.progress = pos.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    currentTime.text = formatTime(pos)
                }
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun setupBatteryAndClock() {
        updateClock()
        setupBatteryReceiver()
    }

    private fun updateClock() {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        clockText.text = time
        handler.postDelayed(this::updateClock, 60000)
    }

    private fun setupBatteryReceiver() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level != -1 && scale != -1) {
                    val percent = level * 100 / scale
                    batteryText.text = "$percent%"
                }
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun loadVideos() {
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        val cursor = contentResolver.query(uri, projection, null, null, null)

        playlist.clear()
        player.clearMediaItems()

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol)
                val videoUri = ContentUris.withAppendedId(uri, id)
                playlist.add(VideoModel(id, name, videoUri))
                player.addMediaItem(MediaItem.fromUri(videoUri))
            }
        }

        if (playlist.isNotEmpty()) {
            videoTitle.text = playlist[0].title
            player.prepare()
            player.playWhenReady = true
            showControls()
        }
    }

    private fun showPlaylist() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_playlist, null)
        val rv = view.findViewById<RecyclerView>(R.id.rv_playlist)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PlaylistAdapter(playlist, currentIndex) { pos ->
            player.seekTo(pos, C.TIME_UNSET)
            player.play()
            dialog.dismiss()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        requestedOrientation = if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        WindowInsetsControllerCompat(window, window.decorView).apply {
            if (isFullscreen) hide(WindowInsetsCompat.Type.systemBars()) else show(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun checkPermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            permissionLauncher.launch(perm)
        }
    }

    private fun formatTime(ms: Long): String {
        if (ms <= 0 || ms == C.TIME_UNSET) return "00:00"
        val totalSecs = ms / 1000
        val secs = totalSecs % 60
        val mins = (totalSecs / 60) % 60
        val hours = totalSecs / 3600
        return if (hours > 0) String.format("%d:%02d:%02d", hours, mins, secs) else String.format("%02d:%02d", mins, secs)
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        handler.removeCallbacksAndMessages(null)
        batteryReceiver?.let { unregisterReceiver(it) }
    }

    inner class PlaylistAdapter(
        private val list: List<VideoModel>,
        private val currentPos: Int,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tv_video_title)
            val thumb: ImageView = view.findViewById(R.id.iv_thumbnail)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_video, parent, false))

        @SuppressLint("NewApi")
        override fun onBindViewHolder(holder: VH, pos: Int) {
            val item = list[pos]
            holder.title.text = item.title
            if (pos == currentPos) {
                holder.title.setTextColor(neonPink)
                holder.title.setShadowLayer(15f, 0f, 0f, neonPink)
                holder.title.setTypeface(null, Typeface.BOLD)
            } else {
                holder.title.setTextColor(Color.WHITE)
                holder.title.setShadowLayer(5f, 0f, 0f, neonCyan)
            }

            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, item.id), Size(320, 180), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(contentResolver, item.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
            }
            holder.thumb.setImageBitmap(bitmap)

            holder.itemView.setOnClickListener { onClick(pos) }
        }

        override fun getItemCount() = list.size
    }
}
