package com.example.webbox

import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GalleryAdapter(private val imageUrls: List<String>, private val refererUrl: String) : RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder>() {
    var onItemClick: ((String) -> Unit)? = null
    class GalleryViewHolder(view: View) : RecyclerView.ViewHolder(view) { val ivImage: ImageView = view as ImageView }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val iv = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
            setPadding(4, 4, 4, 4)
        }
        return GalleryViewHolder(iv)
    }
    override fun onBindViewHolder(holder: GalleryViewHolder, position: Int) {
        val url = imageUrls[position]
        holder.ivImage.setImageDrawable(null)
        holder.ivImage.setOnClickListener { onItemClick?.invoke(url) }
        holder.ivImage.setOnLongClickListener {
            try {
                val context = holder.ivImage.context
                val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "WebBox/" + System.currentTimeMillis() + ".jpg")
                val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                dm.enqueue(request)
                android.widget.Toast.makeText(context, "已开始保存", android.widget.Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(holder.ivImage.context, "保存失败", android.widget.Toast.LENGTH_SHORT).show()
            }
            true
        }

        // 优化：给ImageView设置Tag，防止列表复用时图片错乱
        holder.ivImage.tag = url

        Thread {
            try {
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36")
                if (refererUrl.isNotEmpty()) conn.setRequestProperty("Referer", refererUrl)
                conn.connect()
                val bitmap = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                if (bitmap != null) android.os.Handler(android.os.Looper.getMainLooper()).post {
                    // 只有当图片没被滑走时才显示
                    if (holder.ivImage.tag == url) holder.ivImage.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                try {
                    val bitmap = android.graphics.BitmapFactory.decodeStream(java.net.URL(url).openStream())
                    if (bitmap != null) android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (holder.ivImage.tag == url) holder.ivImage.setImageBitmap(bitmap)
                    }
                } catch (ex: Exception) {}
            }
        }.start()
    }
    override fun getItemCount(): Int = imageUrls.size
}

class SniffedVideoAdapter(private val videoData: List<Triple<String, String, String>>) : RecyclerView.Adapter<SniffedVideoAdapter.VideoViewHolder>() {
    var onItemClick: ((String) -> Unit)? = null
    class VideoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCover: ImageView = view.findViewById(R.id.ivVideoCover)
        val tvName: TextView = view.findViewById(R.id.tvVideoName)
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sniffed_video, parent, false)
        return VideoViewHolder(view)
    }
    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val (url, cover, name) = videoData[position]
        holder.tvName.text = if (name.isNotBlank()) name else url

        // 优化：设置Tag防止封面错乱
        holder.ivCover.tag = cover

        if (cover.isNotEmpty()) Thread {
            try {
                val conn = java.net.URL(cover).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val bitmap = android.graphics.BitmapFactory.decodeStream(conn.inputStream)
                if (bitmap != null) android.os.Handler(android.os.Looper.getMainLooper()).post {
                    if (holder.ivCover.tag == cover) holder.ivCover.setImageBitmap(bitmap)
                }
            } catch (e: Exception) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { holder.ivCover.setImageResource(android.R.drawable.ic_media_play) }
            }
        }.start() else holder.ivCover.setImageResource(android.R.drawable.ic_media_play)
        holder.itemView.setOnClickListener { onItemClick?.invoke(url) }
    }
    override fun getItemCount() = videoData.size
}

class TabListAdapter(
    private var tabs: List<TabData>
) : RecyclerView.Adapter<TabListAdapter.TabViewHolder>() {

    var onItemClick: ((TabData) -> Unit)? = null
    var onTabRemoved: (() -> Unit)? = null // 新增：标签被删除后的回调

    class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(android.R.id.text1)
        val tvDelete: TextView = view.findViewById(android.R.id.text2)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val context = parent.context
        val density = context.resources.displayMetrics.density
        val padding = (16 * density).toInt()

        // 纯代码生成列表项：一个水平线性布局，包含标题和删除按钮
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT
            )
            setPadding(padding, padding, padding, padding)
        }

        val tvTitle = TextView(context).apply {
            id = android.R.id.text1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            textSize = 16f
            setTextColor(Color.WHITE)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }

        val tvDelete = TextView(context).apply {
            id = android.R.id.text2
            text = "×"
            textSize = 24f
            setTextColor(Color.RED)
            setPadding(padding, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        container.addView(tvTitle)
        container.addView(tvDelete)

        return TabViewHolder(container)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]

        // 如果是当前激活的标签，显示橙色，否则显示白色
        if (tab.isActive) {
            holder.tvTitle.setTextColor(Color.parseColor("#ff6d00"))
        } else {
            holder.tvTitle.setTextColor(Color.WHITE)
        }

        // 显示网页标题（如果为空则显示网址）
        holder.tvTitle.text = if (tab.title.isNotBlank()) tab.title else tab.url

        // 点击列表项
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(tab)
        }

        // 点击右侧的删除按钮
        holder.tvDelete.setOnClickListener {
            TabManager.removeTab(tab.id)
            notifyDataSetChanged()
            onTabRemoved?.invoke() // 触发删除回调
        }
    }

    override fun getItemCount(): Int = tabs.size

    fun updateData(newTabs: List<TabData>) {
        tabs = newTabs
        notifyDataSetChanged()
    }
}