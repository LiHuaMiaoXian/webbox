package com.example.webbox

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.webkit.WebView
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper

object ExtractionManager {

    // ==========================================
    // 文本提取
    // ==========================================
    fun extractText(activity: Activity, webView: WebView?) {
        if (webView == null) return

        val extractTextJs = """
            (function() {
                try {
                    var garbage = document.querySelectorAll('script, style, nav, header, footer, aside, iframe, noscript, .ad, .ads, #comments, .sidebar');
                    for (var i = 0; i < garbage.length; i++) { garbage[i].style.display = 'none'; garbage[i].remove(); }
                    var contentNode = document.querySelector('article') || document.querySelector('main') || document.querySelector('.post-content') || document.querySelector('#content') || document.body;
                    var text = contentNode.innerText || "";
                    text = text.replace(/\n\s*\n/g, '\n\n').replace(/^\s+|\s+$/g, ''); 
                    if (text.length < 50) text = document.body.innerText;
                    if (text.length > 50000) text = text.substring(0, 50000) + "\n\n... (内容过长已截断)";
                    return text;
                } catch (e) { return "提取失败: " + e.message; }
            })();
        """.trimIndent()

        webView.evaluateJavascript(extractTextJs) { result ->
            if (result != null && result != "null") {
                val cleanText = result.removePrefix("\"").removeSuffix("\"").replace("\\n", "\n").replace("\\\"", "\"").replace("\\t", "\t").replace("\\\\", "\\")
                if (cleanText.isBlank()) {
                    Toast.makeText(activity, "未能提取到有效文本", Toast.LENGTH_SHORT).show()
                } else {
                    activity.runOnUiThread {
                        activity.findViewById<TextView>(R.id.tvReaderContent)?.text = cleanText
                        activity.findViewById<android.view.View>(R.id.textReaderOverlay)?.visibility = android.view.View.VISIBLE
                        activity.findViewById<android.view.View>(R.id.lockOverlay)?.visibility = android.view.View.GONE
                        activity.findViewById<android.view.View>(R.id.galleryOverlay)?.visibility = android.view.View.GONE
                        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.hideSoftInputFromWindow(webView.windowToken, 0)
                        activity.findViewById<android.widget.ScrollView>(R.id.readerScrollView)?.scrollTo(0, 0)
                    }
                }
            }
        }
    }

    // ==========================================
    // 图片提取（由于涉及到全屏显示和 Adapter，这里只触发提取并回调给 MainActivity）
    // ==========================================
    fun extractImage(activity: MainActivity, webView: WebView?) {
        if (webView == null) return

        val extractJs = """
            (function() {
                var imgs = document.querySelectorAll('img'); var urls = [];
                for (var i = 0; i < imgs.length; i++) {
                    var img = imgs[i]; var candidates = [];
                    if (img.src) candidates.push(img.src);
                    if (img.getAttribute('data-src')) candidates.push(img.getAttribute('data-src'));
                    if (img.getAttribute('data-original')) candidates.push(img.getAttribute('data-original'));
                    var bestUrl = '';
                    for (var k = 0; k < candidates.length; k++) { var u = candidates[k]; if (u && !u.startsWith('data:') && u.length > bestUrl.length) bestUrl = u; }
                    if (bestUrl && bestUrl.startsWith('//')) bestUrl = 'https:' + bestUrl;
                    if (bestUrl && bestUrl.startsWith('http')) urls.push(bestUrl);
                }
                return urls.join('\n');
            })();
        """.trimIndent()

        webView.evaluateJavascript(extractJs) { result ->
            if (result != null && result != "null") {
                val cleanResult = result.trim('"').replace("\\n", "\n")
                if (cleanResult.isBlank()) {
                    Toast.makeText(activity, "未找到图片", Toast.LENGTH_SHORT).show()
                } else {
                    val imageUrls = cleanResult.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                    if (imageUrls.isEmpty()) {
                        Toast.makeText(activity, "未找到图片", Toast.LENGTH_SHORT).show()
                    } else {
                        activity.runOnUiThread {
                            // 调用主界面的图片展示逻辑（保持原样）
                            activity.findViewById<TextView>(R.id.tvGalleryTitle)?.text = "已提取 ${imageUrls.size} 张图片"
                            val ivFullScreenImage = activity.findViewById<android.widget.ImageView>(R.id.ivFullScreenImage)
                            ivFullScreenImage?.setOnClickListener {
                                ivFullScreenImage.visibility = android.view.View.GONE
                                ivFullScreenImage.setImageDrawable(null)
                            }
                            activity.findViewById<TextView>(R.id.btnCloseGallery)?.setOnClickListener {
                                activity.findViewById<android.view.View>(R.id.galleryOverlay)?.visibility = android.view.View.GONE
                                ivFullScreenImage?.visibility = android.view.View.GONE
                                ivFullScreenImage?.setImageDrawable(null)
                            }

                            val adapter = GalleryAdapter(imageUrls, activity.currentPreviewUrl ?: webView.url ?: "")
                            adapter.onItemClick = { clickedUrl ->
                                ivFullScreenImage?.setImageDrawable(null)
                                ivFullScreenImage?.visibility = android.view.View.VISIBLE
                                Thread {
                                    try {
                                        val options = android.graphics.BitmapFactory.Options()
                                        options.inJustDecodeBounds = true
                                        java.net.URL(clickedUrl).openStream().use { android.graphics.BitmapFactory.decodeStream(it, null, options) }
                                        var inSampleSize = 1
                                        val reqWidth = activity.resources.displayMetrics.widthPixels
                                        val reqHeight = activity.resources.displayMetrics.heightPixels
                                        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
                                            val halfHeight = options.outHeight / 2
                                            val halfWidth = options.outWidth / 2
                                            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                                                inSampleSize *= 2
                                            }
                                        }
                                        val realOptions = android.graphics.BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                                        val bitmap = java.net.URL(clickedUrl).openStream().use { android.graphics.BitmapFactory.decodeStream(it, null, realOptions) }
                                        if (bitmap != null) activity.runOnUiThread { ivFullScreenImage?.setImageBitmap(bitmap) }
                                    } catch (e: Exception) {}
                                }.start()
                            }
                            activity.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvGallery)?.adapter = adapter
                            activity.findViewById<android.view.View>(R.id.galleryOverlay)?.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // 视频提取（直接调主界面的方法）
    // ==========================================
    fun extractVideo(activity: MainActivity, webView: WebView?) {
        // 视频提取逻辑依然在 MainActivity 的 showMediaExtractDialog 里
        activity.showMediaExtractDialog(true)
    }

    // ==========================================
    // 新增：链接提取
    // ==========================================
    fun extractLinks(activity: Activity, webView: WebView?) {
        if (webView == null) return

        // 注入 JS，获取文字和链接，用 "|||" 拼接防止冲突
        val extractJs = """
            (function() {
                var links = document.querySelectorAll('a');
                var result = [];
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].href;
                    var text = links[i].innerText.trim();
                    if (href && href.startsWith('http') && !href.startsWith('javascript:')) {
                        if (!text) text = "无标题链接";
                        // 防止数据里的竖线破坏分割，把标题里的换行去掉
                        text = text.replace(/\n/g, ' '); 
                        result.push(text + "|||" + href);
                    }
                }
                return result.join('\n');
            })();
        """.trimIndent()

        webView.evaluateJavascript(extractJs) { result ->
            if (result == null || result == "null" || result.isBlank()) {
                Toast.makeText(activity, "未找到任何有效链接", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }

            val cleanResult = result.trim('"').replace("\\n", "\n")
            val links = cleanResult.split("\n").map { it.trim() }.filter { it.isNotEmpty() && it.contains("|||") }.distinct()

            if (links.isEmpty()) {
                Toast.makeText(activity, "未找到任何有效链接", Toast.LENGTH_SHORT).show()
                return@evaluateJavascript
            }

            activity.runOnUiThread {
                // 1. 构建纯白底黑字弹窗
                val wrapper = ContextThemeWrapper(activity, android.R.style.Theme_Material_Light_Dialog_Alert)
                val builder = android.app.AlertDialog.Builder(wrapper)
                builder.setTitle("提取到 ${links.size} 个链接")

                // 2. 构建显示列表（只显示 "标题\n网址"，看起来最直观）
                val displayItems = links.map {
                    val parts = it.split("|||")
                    "${parts[0]}\n${parts[1]}"
                }.toTypedArray()

                builder.setItems(displayItems) { _, which ->
                    val rawLink = links[which].split("|||")[1]
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("link", rawLink))
                    Toast.makeText(activity, "已复制链接", Toast.LENGTH_SHORT).show()
                }

                // 3. 底部添加"复制全部"按钮
                builder.setNeutralButton("复制全部") { _, _ ->
                    val sb = StringBuilder()
                    links.forEach {
                        val parts = it.split("|||")
                        sb.append("${parts[0]}: ${parts[1]}\n")
                    }
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("all_links", sb.toString()))
                    Toast.makeText(activity, "已复制全部 ${links.size} 个链接", Toast.LENGTH_SHORT).show()
                }

                builder.setNegativeButton("取消", null)
                builder.show()
            }
        }
    }
}