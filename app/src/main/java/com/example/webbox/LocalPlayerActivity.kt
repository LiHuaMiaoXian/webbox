package com.example.webbox

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

class LocalPlayerActivity : AppCompatActivity() {

    private lateinit var exoPlayer: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var rvPlaylist: RecyclerView
    private lateinit var playlistAdapter: PlaylistAdapter
    private val playlist = mutableListOf<Uri>()

    private lateinit var btnPlayPause: TextView
    private lateinit var btnRepeatMode: TextView
    private lateinit var topTitleBar: View
    private lateinit var bottomControlLayout: View

    private var isFullScreen = false

    private val openMultipleMediaLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            playlist.clear()
            playlist.addAll(uris)
            playlistAdapter.notifyDataSetChanged()

            // 将列表加入 ExoPlayer
            exoPlayer.clearMediaItems()
            uris.forEach { uri ->
                val mediaItem = MediaItem.fromUri(uri)
                exoPlayer.addMediaItem(mediaItem)
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            updatePlayPauseBtn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_local_player)

        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 初始化播放器视图
        playerView = PlayerView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // 使用 ExoPlayer 自带的控制条
            useController = true
        }
        findViewById<ViewGroup>(R.id.playerContainer).addView(playerView)

        // ==========================================
        // 核心：初始化 ExoPlayer，并注入防盗链 Referer
        // ==========================================
        // 1. 接收从 MainActivity 传过来的防盗链来源
        val refererUrl = intent.getStringExtra("REFERER_URL") ?: "https://www.baidu.com"

        // 2. 构建网络请求工厂，设置请求头
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent("Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36")
            setDefaultRequestProperties(mapOf("Referer" to refererUrl))
            setAllowCrossProtocolRedirects(true) // 允许跨协议重定向，防部分流 302 报错
        }

        // 3. 把工厂装进 MediaSourceFactory，最后给 ExoPlayer 使用
        val mediaSourceFactory = DefaultMediaSourceFactory(httpDataSourceFactory)
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        playerView.player = exoPlayer

        // 初始化列表
        rvPlaylist = findViewById(R.id.rvPlaylist)
        playlistAdapter = PlaylistAdapter()
        rvPlaylist.layoutManager = LinearLayoutManager(this)
        rvPlaylist.adapter = playlistAdapter

        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnRepeatMode = findViewById(R.id.btnRepeatMode)
        topTitleBar = findViewById(R.id.topTitleBar)
        bottomControlLayout = findViewById(R.id.bottomControlLayout)

        // ==========================================
        // 👇 核心：接收从 MainActivity 浏览器传过来的抓取列表 👇
        // ==========================================
        val incomingUrl = intent.getStringExtra("VIDEO_URL")
        val incomingPlaylist = intent.getStringArrayListExtra("VIDEO_PLAYLIST")

        if (!incomingPlaylist.isNullOrEmpty()) {
            playlist.clear()
            // 把网络链接转成 Uri 填入侧边列表
            incomingPlaylist.forEach { urlString ->
                playlist.add(Uri.parse(urlString))
            }
            playlistAdapter.notifyDataSetChanged()

            // 把链接塞给底层播放器
            exoPlayer.clearMediaItems()
            incomingPlaylist.forEach { urlString ->
                exoPlayer.addMediaItem(MediaItem.fromUri(urlString))
            }

            // 如果是单独点开的某个视频，就自动跳转到那一个开始播
            if (!incomingUrl.isNullOrEmpty()) {
                val startIndex = incomingPlaylist.indexOf(incomingUrl)
                if (startIndex != -1) {
                    exoPlayer.seekTo(startIndex, 0)
                }
            }
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            updatePlayPauseBtn()
        } else if (!incomingUrl.isNullOrEmpty()) {
            // 如果只传了一个链接，没有列表的情况
            playlist.clear()
            playlist.add(Uri.parse(incomingUrl))
            playlistAdapter.notifyDataSetChanged()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(MediaItem.fromUri(incomingUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            updatePlayPauseBtn()
        }
        // ==========================================
        // 👆 接收逻辑结束 👆
        // ==========================================

        findViewById<TextView>(R.id.btnClosePlayer).setOnClickListener { finish() }

        findViewById<TextView>(R.id.btnSelectMedia).setOnClickListener {
            openMultipleMediaLauncher.launch(arrayOf("video/*", "audio/*"))
        }

        // 播放/暂停控制
        btnPlayPause.setOnClickListener {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                exoPlayer.play()
            }
            updatePlayPauseBtn()
        }

        // 上一集/下一集
        findViewById<TextView>(R.id.btnPrev).setOnClickListener {
            if (exoPlayer.hasPreviousMediaItem()) {
                exoPlayer.seekToPreviousMediaItem()
            }
        }
        findViewById<TextView>(R.id.btnNext).setOnClickListener {
            if (exoPlayer.hasNextMediaItem()) {
                exoPlayer.seekToNextMediaItem()
            }
        }

        // 循环模式切换 (列表循环 -> 单曲循环 -> 随机播放)
        btnRepeatMode.setOnClickListener {
            when (exoPlayer.repeatMode) {
                Player.REPEAT_MODE_ALL -> {
                    if (exoPlayer.shuffleModeEnabled) {
                        // 如果当前是随机，切回单曲循环
                        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                        exoPlayer.shuffleModeEnabled = false
                        btnRepeatMode.text = "单曲循环"
                    } else {
                        // 如果当前是列表，切到单曲循环
                        exoPlayer.repeatMode = Player.REPEAT_MODE_ONE
                        btnRepeatMode.text = "单曲循环"
                    }
                }
                Player.REPEAT_MODE_ONE -> {
                    // 切到随机播放
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                    exoPlayer.shuffleModeEnabled = true
                    btnRepeatMode.text = "随机播放"
                }
                else -> {
                    // 切回列表循环
                    exoPlayer.repeatMode = Player.REPEAT_MODE_ALL
                    exoPlayer.shuffleModeEnabled = false
                    btnRepeatMode.text = "列表循环"
                }
            }
        }

        // 全屏按钮点击
        findViewById<TextView>(R.id.btnFullScreen).setOnClickListener {
            toggleFullScreen()
        }

        // 监听播放状态变化，自动更新按钮文字
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayPauseBtn()
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                rvPlaylist.smoothScrollToPosition(exoPlayer.currentMediaItemIndex)
                playlistAdapter.notifyDataSetChanged()
            }
        })

        // 处理物理返回键：如果在全屏，先退出全屏
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullScreen) {
                    toggleFullScreen()
                } else {
                    finish()
                }
            }
        })
    }

    private fun toggleFullScreen() {
        isFullScreen = !isFullScreen
        if (isFullScreen) {
            // 进入全屏：隐藏所有 UI，强制横屏，隐藏状态栏
            topTitleBar.visibility = View.GONE
            bottomControlLayout.visibility = View.GONE
            rvPlaylist.visibility = View.GONE // 隐藏列表

            // 如果那个“导入本地”按钮在某个特定的容器里，也把它隐藏
            findViewById<View>(R.id.btnSelectMedia)?.visibility = View.GONE
            // 如果你的控件名叫别的，把上面的 ID 替换掉

            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            // 退出全屏：恢复所有 UI，强制竖屏
            topTitleBar.visibility = View.VISIBLE
            bottomControlLayout.visibility = View.VISIBLE
            rvPlaylist.visibility = View.VISIBLE
            findViewById<View>(R.id.btnSelectMedia)?.visibility = View.VISIBLE

            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    private fun updatePlayPauseBtn() {
        btnPlayPause.text = if (exoPlayer.isPlaying) "暂停" else "播放"
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer.release()
    }

    inner class PlaylistAdapter : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(android.R.id.text1)
            val root: View = view
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_1, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val uri = playlist[position]

            // 智能获取名字：网络流不显示乱码，本地文件显示文件名
            var name = uri.lastPathSegment ?: "未知媒体 ${position + 1}"
            if (uri.scheme == "http" || uri.scheme == "https") {
                name = "网络流 ${position + 1}"
            }

            if (position == exoPlayer.currentMediaItemIndex) {
                holder.tvName.text = "▶ 正在播放: $name"
                // 动态获取全局主题色
                holder.tvName.setTextColor(ThemeManager.getThemeColor(this@LocalPlayerActivity))
            } else {
                holder.tvName.text = "第 ${position + 1} 集: $name"
                holder.tvName.setTextColor(ContextCompat.getColor(this@LocalPlayerActivity, R.color.white))
            }

            holder.tvName.setPadding(20, 20, 20, 20)
            holder.root.setOnClickListener {
                exoPlayer.seekTo(position, 0)
                exoPlayer.playWhenReady = true
            }
        }

        override fun getItemCount() = playlist.size
    }
}