package com.example.webbox

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.net.URI

object FloatingWindowManager {

    @SuppressLint("ClickableViewAccessibility")
    fun createFloatingWindow(activity: MainActivity) {
        val currentUrl = activity.webView1?.url
        if (currentUrl == null || currentUrl.startsWith("about:")) {
            Toast.makeText(activity, "当前无网页", Toast.LENGTH_SHORT).show()
            return
        }

        val themeColor = ThemeManager.getThemeColor(activity)

        val floatRoot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(activity, R.color.black_bg))
            val screenWidth = activity.resources.displayMetrics.widthPixels
            val screenHeight = activity.resources.displayMetrics.heightPixels
            layoutParams = RelativeLayout.LayoutParams((screenWidth * 0.85).toInt(), (screenHeight * 0.65).toInt()).apply { leftMargin = 100; topMargin = 200 }
            background = GradientDrawable().apply { setStroke(4, themeColor) }
            tag = activity.floatTag
        }

        val titleBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(themeColor)
            setPadding(15, 10, 15, 10)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val dragHandle = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
        }
        val titleTv = TextView(activity).apply {
            text = try { URI(currentUrl).host } catch (e: Exception) { "浮动窗口" }
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        dragHandle.addView(titleTv, LinearLayout.LayoutParams(-1, -2))

        val changeBtn = TextView(activity).apply { text = "✎"; setTextColor(Color.WHITE); textSize = 16f; setPadding(15, 0, 15, 0) }
        val refreshBtn = TextView(activity).apply { text = "↻"; setTextColor(Color.WHITE); textSize = 16f; setPadding(15, 0, 15, 0) }
        val zoomOut = TextView(activity).apply { text = "−"; setTextColor(Color.WHITE); setPadding(10, 0, 10, 0) }
        val zoomReset = TextView(activity).apply { text = "1:1"; setTextColor(Color.WHITE); setPadding(10, 0, 10, 0) }
        val zoomIn = TextView(activity).apply { text = "+"; setTextColor(Color.WHITE); setPadding(10, 0, 10, 0) }

        titleBar.addView(dragHandle); titleBar.addView(zoomOut); titleBar.addView(zoomReset); titleBar.addView(zoomIn); titleBar.addView(changeBtn); titleBar.addView(refreshBtn)
        floatRoot.addView(titleBar)

        val fwWeb = WebView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.builtInZoomControls = true; settings.displayZoomControls = false; settings.setSupportZoom(true)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    if (request == null) return null
                    if (!activity.adBlockEnabled || request.isForMainFrame) return super.shouldInterceptRequest(view, request)
                    val url = request.url?.toString()?.lowercase() ?: return super.shouldInterceptRequest(view, request)
                    try { val requestHost = request.url?.host ?: ""; for (whiteHost in activity.adWhiteList) { if (requestHost.contains(whiteHost)) return super.shouldInterceptRequest(view, request) } } catch (e: Exception) {}
                    for (host in activity.defaultAdHosts) { if (url.contains(host)) return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream("".toByteArray())) }
                    if (activity.customAdRuleList.isNotEmpty()) { for (rule in activity.customAdRuleList) { if (url.contains(rule)) return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream("".toByteArray())) } }
                    return super.shouldInterceptRequest(view, request)
                }
            }
            webChromeClient = WebChromeClient(); loadUrl(currentUrl)
        }
        floatRoot.addView(fwWeb)

        val bottomBar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(themeColor)
            setPadding(15, 10, 15, 10)
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val resizeBtn = TextView(activity).apply {
            text = "拖拽缩放"
            setTextColor(Color.WHITE); textSize = 14f
            setBackgroundColor(ContextCompat.getColor(activity, R.color.pink_dark))
            gravity = Gravity.CENTER
            setPadding(20, 10, 20, 10)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = 10 }
        }
        bottomBar.addView(resizeBtn)
        floatRoot.addView(bottomBar)

        changeBtn.setOnClickListener {
            val inputUrl = EditText(activity).apply { setText(currentUrl) }
            AlertDialog.Builder(activity).setTitle("换网址").setView(inputUrl).setPositiveButton("确定") { _, _ ->
                val newUrl = inputUrl.text.toString().trim()
                if (newUrl.isNotEmpty()) fwWeb.loadUrl(if (!newUrl.startsWith("http")) "http://$newUrl" else newUrl)
            }.setNegativeButton("取消", null).show()
        }
        refreshBtn.setOnClickListener { fwWeb.reload() }
        zoomOut.setOnClickListener { fwWeb.settings.textZoom = (fwWeb.settings.textZoom - 10).coerceAtLeast(30) }
        zoomReset.setOnClickListener { fwWeb.settings.textZoom = 100 }
        zoomIn.setOnClickListener { fwWeb.settings.textZoom = (fwWeb.settings.textZoom + 10).coerceAtMost(300) }

        var lastX = 0f; var lastY = 0f
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { lastX = event.rawX; lastY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val lp = floatRoot.layoutParams as RelativeLayout.LayoutParams
                    lp.leftMargin = (lp.leftMargin + (event.rawX - lastX).toInt()).coerceIn(0, activity.resources.displayMetrics.widthPixels - floatRoot.width)
                    lp.topMargin = (lp.topMargin + (event.rawY - lastY).toInt()).coerceIn(0, activity.resources.displayMetrics.heightPixels - floatRoot.height)
                    floatRoot.layoutParams = lp; lastX = event.rawX; lastY = event.rawY; true
                }
                else -> false
            }
        }
        var rsW = 0; var rsH = 0; var rsX = 0f; var rsY = 0f
        resizeBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { rsX = event.rawX; rsY = event.rawY; rsW = floatRoot.width; rsH = floatRoot.height; true }
                MotionEvent.ACTION_MOVE -> {
                    val lp = floatRoot.layoutParams as RelativeLayout.LayoutParams
                    lp.width = (rsW + (event.rawX - rsX).toInt()).coerceAtLeast(400)
                    lp.height = (rsH + (event.rawY - rsY).toInt()).coerceAtLeast(300)
                    floatRoot.layoutParams = lp; true
                }
                else -> false
            }
        }
        activity.findViewById<ViewGroup>(R.id.mainRoot)?.addView(floatRoot)
    }
}