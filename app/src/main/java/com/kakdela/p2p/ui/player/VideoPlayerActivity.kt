package com.kakdela.p2p.ui.player

import android.Manifest
import android.content.ContentUris
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.AlphaAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.kakdela.p2p.R

data class VideoModel(val title: String, val uri: android.net.Uri)

class VideoPlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: ImageButton
    private lateinit var btnPrev: ImageButton
    private lateinit var btnFullscreen: ImageButton
    private lateinit var btnPlaylist: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var currentTime: TextView
    private lateinit var totalTime: TextView
    private lateinit var videoTitle: TextView
    private lateinit var controlsRoot: View

    private val hideHandler = Handler(Looper.getMainLooper())
    private val hideRunnable = Runnable { hideControls() }
    private var isFullscreen = false
    private var videoPlaylist = mutableListOf<VideoModel>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> if (isGranted) loadVideosFromDevice() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        initViews()
        initializePlayer()
        setupControls()
        checkPermissionsAndLoad()
    }

    private fun initViews() {
        playerView = findViewById(R.id.player_view)
        controlsRoot = findViewById(R.id.controls_root)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnNext = findViewById(R.id.btn_next)
        btnPrev = findViewById(R.id.btn_prev)
        btnFullscreen = findViewById(R.id.btn_fullscreen)
        btnPlaylist = findViewById(R.id.btn_playlist)
        seekBar = findViewById(R.id.seek_bar)
        currentTime = findViewById(R.id.current_time)
        totalTime = findViewById(R.id.total_time)
        videoTitle = findViewById(R.id.video_title)
    }

    private fun initializePlayer() {
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        player = ExoPlayer.Builder(this, renderersFactory).build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val index = player.currentMediaItemIndex
                if (index < videoPlaylist.size) {
                    videoTitle.text = videoPlaylist[index].title
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                if (isPlaying) startSeekBarUpdate()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) totalTime.text = formatTime(player.duration)
            }
        })
    }

    private fun loadVideosFromDevice() {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
        val query = contentResolver.query(collection, projection, null, null, "${MediaStore.Video.Media.DISPLAY_NAME} ASC")

        query?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            
            videoPlaylist.clear()
            player.clearMediaItems()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                videoPlaylist.add(VideoModel(name, uri))
                player.addMediaItem(MediaItem.fromUri(uri))
            }
        }
        player.prepare()
    }

    private fun showPlaylistDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_playlist, null)
        val rv = view.findViewById<RecyclerView>(R.id.rv_playlist)
        
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = PlaylistAdapter(videoPlaylist) { index ->
            player.seekTo(index, 0)
            player.play()
            dialog.dismiss()
        }
        
        dialog.setContentView(view)
        dialog.show()
    }

    private fun setupControls() {
        btnPlayPause.setOnClickListener { if (player.isPlaying) player.pause() else player.play(); resetHideTimer() }
        btnNext.setOnClickListener { if (player.hasNextMediaItem()) player.seekToNextMediaItem(); resetHideTimer() }
        btnPrev.setOnClickListener { if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem(); resetHideTimer() }
        btnFullscreen.setOnClickListener { toggleFullscreen(); resetHideTimer() }
        btnPlaylist.setOnClickListener { showPlaylistDialog() }
        playerView.setOnClickListener { if (controlsRoot.visibility == View.VISIBLE) hideControls() else showControls() }
    }

    // --- Вспомогательные методы (UI, Таймеры, Fullscreen) ---
    private fun startSeekBarUpdate() {
        hideHandler.post(object : Runnable {
            override fun run() {
                if (player.isPlaying) {
                    seekBar.progress = (player.currentPosition * 1000 / player.duration.coerceAtLeast(1)).toInt()
                    currentTime.text = formatTime(player.currentPosition)
                    hideHandler.postDelayed(this, 1000)
                }
            }
        })
    }

    private fun formatTime(ms: Long): String {
        val s = (ms / 1000) % 60
        val m = (ms / (1000 * 60)) % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        requestedOrientation = if (isFullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (isFullscreen) hideSystemUi() else showSystemUi()
    }

    private fun hideSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
    }

    private fun showSystemUi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        }
    }

    private fun showControls() { controlsRoot.visibility = View.VISIBLE; resetHideTimer() }
    private fun hideControls() { controlsRoot.visibility = View.GONE }
    private fun resetHideTimer() { hideHandler.removeCallbacks(hideRunnable); hideHandler.postDelayed(hideRunnable, 5000) }

    private fun checkPermissionsAndLoad() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) loadVideosFromDevice() else requestPermissionLauncher.launch(perm)
    }

    override fun onDestroy() { super.onDestroy(); player.release() }

    // --- Адаптер для списка видео ---
    inner class PlaylistAdapter(val list: List<VideoModel>, val onClick: (Int) -> Unit) : RecyclerView.Adapter<PlaylistAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title = v.findViewById<TextView>(R.id.tv_video_title)
            val thumb = v.findViewById<ImageView>(R.id.iv_thumbnail)
        }
        override fun onCreateViewHolder(p: ViewGroup, t: Int) = VH(LayoutInflater.from(p.context).inflate(R.layout.item_video, p, false))
        override fun onBindViewHolder(h: VH, p: Int) {
            val item = list[p]
            h.title.text = item.title
            // Загрузка миниатюры
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentResolver.loadThumbnail(item.uri, Size(200, 120), null)
                } else {
                    MediaStore.Video.Thumbnails.getThumbnail(contentResolver, ContentUris.parseId(item.uri), MediaStore.Video.Thumbnails.MINI_KIND, null)
                }
                h.thumb.setImageBitmap(bitmap)
            } catch (e: Exception) { h.thumb.setImageResource(android.R.drawable.ic_menu_gallery) }
            
            h.itemView.setOnClickListener { onClick(p) }
        }
        override fun getItemCount() = list.size
    }
}

