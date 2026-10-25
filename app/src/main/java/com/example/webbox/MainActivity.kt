package com.example.webbox

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.houbb.opencc4j.util.ZhConverterUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    lateinit var db: AppDatabase
    lateinit var adapter: WebsiteAdapter
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var topPanel: LinearLayout
    private lateinit var bottomPanel: LinearLayout
    private lateinit var infoBar: TextView
    private lateinit var websiteList: RecyclerView
    private lateinit var zoneTabsContainer: LinearLayout
    lateinit var previewContainer: FrameLayout

    private lateinit var galleryOverlay: LinearLayout
    private lateinit var rvGallery: RecyclerView
    private lateinit var tvGalleryTitle: TextView
    private lateinit var webProgressBar: ProgressBar
    private lateinit var previewPlaceholder: LinearLayout
    private lateinit var bottomNavBar: LinearLayout
    private lateinit var sbBuiltinContainer: LinearLayout
    lateinit var ivBackground: ImageView

    var currentZone = "默认分区"
    private var currentFilter = "all"
    private var searchKeyword = ""
    private var excludeVisitedOnly = false
    var allZones = listOf<ZoneEntity>()
    private var allWebsitesInZone = listOf<WebsiteEntity>()

    private var isSelectMode = false
    val selectedUrls = mutableSetOf<String>()
    private var batchBar: LinearLayout? = null

    var webView1: WebView? = null
    var webView2: WebView? = null
    private var layoutMode = "single"
    var activePane = 1
    private var sharedZoom = 1.0f
    private var lastClickTime = 0L
    private var lastAlertTime = 0L
    private var failureView: LinearLayout? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private var pauseBg = true
    private var isFullScreenMode = false
    private var lastFullScreenToggleTime = 0L
    private var unloadHidden = true
    private var disableScale = false
    private var isMuted = false
    private var isEyeCareMode = false
    var isBlockingMode = false

    var isPrivateMode = false
    var privatePassword: String? = null

    var adBlockEnabled = true
    val defaultAdHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
        "adnxs.com", "popads.net", "propellerads.com", "exoclick.com", "juicyads.com",
        "trafficjunky.net", "tsyndicate.com", "taboola.com", "outbrain.com", "bet365.com",
        "1xbet.com", "adserver.", "adservice."
    )
    var customAdRuleList = mutableListOf<String>()
    var adWhiteList = mutableSetOf<String>()

    lateinit var prefs: android.content.SharedPreferences
    var customCssRules = mutableSetOf<String>()
    var translateApiUrl = ""
    var translateApiKey = ""

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (filePathCallback == null) return@registerForActivityResult
        var results: Array<Uri>? = null
        if (result.resultCode == RESULT_OK) {
            val dataString = result.data?.dataString
            val clipData = result.data?.clipData
            if (dataString != null) results = arrayOf(Uri.parse(dataString))
            else if (clipData != null) {
                val list = ArrayList<Uri>()
                for (i in 0 until clipData.itemCount) list.add(clipData.getItemAt(i).uri)
                results = list.toTypedArray()
            }
        }
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    val pickImageLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e: Exception) {}
            try { prefs.edit().putString("custom_bg_uri", uri.toString()).apply(); loadCustomBackground(); Toast.makeText(this, "背景已更新", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(this, "设置失败", Toast.LENGTH_SHORT).show() }
        }
    }

    var jsonExportContent = ""
    val createJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.openOutputStream(uri)?.use { it.write(jsonExportContent.toByteArray()) }; Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show() }
        }
    }
    val openJsonLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try { val inputStream = contentResolver.openInputStream(uri); val reader = BufferedReader(InputStreamReader(inputStream)); val jsonStr = reader.readText(); reader.close(); DataBackupManager(this@MainActivity).importDataFromJson(jsonStr) }
                catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "导入失败", Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    var htmlExportContent = ""
    val createHtmlLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.openOutputStream(uri)?.use { it.write(htmlExportContent.toByteArray()) }; Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show() }
        }
    }
    val openHtmlLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try { val inputStream = contentResolver.openInputStream(uri); val reader = BufferedReader(InputStreamReader(inputStream)); val htmlStr = reader.readText(); reader.close(); DataBackupManager(this@MainActivity).importHtmlBookmarks(htmlStr) }
                catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "导入失败", Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    var noteExportContent = ""
    val createNoteLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            try { contentResolver.openOutputStream(uri)?.use { it.write(noteExportContent.toByteArray()) }; Toast.makeText(this, "导出成功", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(this, "导出失败", Toast.LENGTH_SHORT).show() }
        }
    }
    val openNoteLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (text.isNotBlank()) {
                        db.appDao().insertNotepad(NotepadEntity(title = "导入的记事", translations = text))
                        withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "导入成功", Toast.LENGTH_SHORT).show(); NotepadDialog(this@MainActivity).show() }
                    }
                } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "导入失败", Toast.LENGTH_SHORT).show() } }
            }
        }
    }

    val createWordLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch {
                val list = db.appDao().searchNotepad("")
                val sb = StringBuilder()
                list.forEach { sb.append("${it.title}\n${it.translations}\n\n") }
                contentResolver.openOutputStream(uri)?.use { it.write(sb.toString().toByteArray()) }
                Toast.makeText(this@MainActivity, "记词导出成功", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val openWordLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                withContext(Dispatchers.Main) {
                    if (text.isNotBlank()) {
                        db.appDao().insertNotepad(NotepadEntity(title = "导入的记词", translations = text))
                        Toast.makeText(this@MainActivity, "导入成功", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private var isRandomBtnFixed = false
    var currentPreviewUrl: String? = null
    private var appLockPassword: String? = null
    private var lastPauseTime: Long = 0L

    val sniffedVideoUrls = mutableSetOf<String>()

    private var lastVisitedUrl: String? = null

    private var autoJob: Job? = null
    private var isAutoRunning = false
    private val randomPlaylist = mutableListOf<WebsiteEntity>()
    private var autoMinSec = 10
    private var autoMaxSec = 20
    var floatTag = "FLOAT_WINDOW_TAG"

    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideUiRunnable = Runnable {
        findViewById<View>(R.id.fabContainer)?.let { it.animate().alpha(0f).setDuration(500).withEndAction { it.visibility = View.GONE }.start() }
        findViewById<View>(R.id.leftFabContainer)?.let { it.animate().alpha(0f).setDuration(500).withEndAction { it.visibility = View.GONE }.start() }
        findViewById<View>(R.id.sidebarToggle)?.let { it.animate().alpha(0f).setDuration(500).withEndAction { it.visibility = View.GONE }.start() }

        if (!isRandomBtnFixed) {
            findViewById<View>(R.id.randomBtn)?.let { it.animate().alpha(0f).setDuration(500).withEndAction { it.visibility = View.GONE }.start() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rootView = window.decorView
            rootView.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                val exclusionRects = mutableListOf<android.graphics.Rect>()
                val density = resources.displayMetrics.density
                val exclusionWidth = (30 * density).toInt()
                exclusionRects.add(android.graphics.Rect(left, top, left + exclusionWidth, bottom))
                exclusionRects.add(android.graphics.Rect(right - exclusionWidth, top, right, bottom))
                v.systemGestureExclusionRects = exclusionRects
            }
        }

        prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        privatePassword = prefs.getString("private_system_pwd", null)
        isPrivateMode = prefs.getBoolean("is_private_mode", false)
        val dbName = if (isPrivateMode) "private_system.db" else "public_system.db"
        db = AppDatabase.getDatabase(this, dbName)

        translateApiUrl = prefs.getString("translate_api_url", "") ?: ""
        translateApiKey = prefs.getString("translate_api_key", "") ?: ""
        appLockPassword = prefs.getString("app_lock_pwd", null)
        adBlockEnabled = prefs.getBoolean("ad_block_enabled", true)
        customCssRules = prefs.getStringSet("custom_css_rules", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        loadCustomAdRules()
        loadAdWhiteList()
        initViews()
        setupWebViews()
        setupListeners()
        setupBackPressedHandler()
        loadData()
        resetUiTimer()
        handleSharedIntent(intent)


    }

    override fun onStop() {
        super.onStop()
        prefs.edit().apply {
            putLong("last_exit_time", System.currentTimeMillis())
            putString("last_zone", currentZone)
            putString("last_layout", layoutMode)
            putInt("last_active_pane", activePane)
            putString("last_url1", webView1?.url)
            putString("last_url2", webView2?.url)
            apply()
        }
        lastPauseTime = System.currentTimeMillis()
    }

    override fun onPause() {
        super.onPause()
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        webView1?.onPause(); webView2?.onPause()
    }

    override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (activePane == 1) webView1?.onResume() else webView2?.onResume()
        if (appLockPassword != null) {
            if (lastPauseTime == 0L || System.currentTimeMillis() - lastPauseTime > 5000) {
                findViewById<LinearLayout>(R.id.lockOverlay).visibility = View.VISIBLE
            }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); handleSharedIntent(intent) }

    override fun onDestroy() {
        super.onDestroy()
        uiHandler.removeCallbacks(hideUiRunnable)
        webView1?.let { web -> (web.parent as? ViewGroup)?.removeView(web); web.stopLoading(); web.clearHistory(); web.clearCache(true); web.removeAllViews(); web.destroy() }
        webView1 = null
        webView2?.let { web -> (web.parent as? ViewGroup)?.removeView(web); web.stopLoading(); web.clearHistory(); web.clearCache(true); web.removeAllViews(); web.destroy() }
        webView2 = null
        if (customView != null) { (customView?.parent as? ViewGroup)?.removeView(customView); customView = null; customViewCallback = null }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        topPanel = findViewById(R.id.topPanel)
        bottomPanel = findViewById(R.id.bottomPanel)
        infoBar = findViewById(R.id.infoBar)
        websiteList = findViewById(R.id.websiteList)
        zoneTabsContainer = findViewById(R.id.zoneTabsContainer)
        previewContainer = findViewById(R.id.previewContainer)
        galleryOverlay = findViewById(R.id.galleryOverlay)
        rvGallery = findViewById(R.id.rvGallery)
        tvGalleryTitle = findViewById(R.id.tvGalleryTitle)
        rvGallery.layoutManager = androidx.recyclerview.widget.StaggeredGridLayoutManager(2, androidx.recyclerview.widget.StaggeredGridLayoutManager.VERTICAL)
        webProgressBar = findViewById(R.id.webProgressBar)
        previewPlaceholder = findViewById(R.id.previewPlaceholder)
        bottomNavBar = findViewById(R.id.bottomNavBar)
        sbBuiltinContainer = findViewById(R.id.sbBuiltinList)
        ivBackground = findViewById(R.id.ivBackground)
        adapter = WebsiteAdapter()
        websiteList.layoutManager = LinearLayoutManager(this)
        websiteList.adapter = adapter

        // 🔥 核心修复：彻底封锁预览层的触摸穿透，防止划到背后列表
        previewContainer.isClickable = true
        previewContainer.isFocusable = true
        bottomPanel.isClickable = true
        bottomPanel.isFocusable = true
    }

    fun loadCustomBackground() {
        val customUriStr = prefs.getString("custom_bg_uri", null)
        if (customUriStr != null) {
            try {
                val uri = Uri.parse(customUriStr)
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                val maxWidth = resources.displayMetrics.widthPixels
                val maxHeight = resources.displayMetrics.heightPixels
                var inSampleSize = 1
                if (options.outHeight > maxHeight || options.outWidth > maxWidth) {
                    val halfHeight = options.outHeight / 2
                    val halfWidth = options.outWidth / 2
                    while ((halfHeight / inSampleSize) >= maxHeight && (halfWidth / inSampleSize) >= maxWidth) inSampleSize *= 2
                }
                val realOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
                val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, realOptions) }
                if (bitmap != null) { ivBackground.setImageBitmap(bitmap); ivBackground.visibility = View.VISIBLE; return }
            } catch (e: Exception) {}
        }
        ivBackground.visibility = View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebViews() { webView1 = createWebView(1); webView2 = createWebView(2) }

    private fun createWebView(paneNum: Int): WebView {
        val webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        webView.layoutParams = FrameLayout.LayoutParams(-1, -1)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.builtInZoomControls = !disableScale
        settings.displayZoomControls = false
        settings.setGeolocationEnabled(true)

        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        CookieManager.getInstance().setAcceptCookie(true)

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                var fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                if (contentDisposition != null) {
                    val match = Regex("filename=\"?(.*?)\"?;", RegexOption.IGNORE_CASE).find(contentDisposition)
                    if (match != null) fileName = URLDecoder.decode(match.groupValues[1], StandardCharsets.UTF_8.name())
                }
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                Toast.makeText(this@MainActivity, "开始下载...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { Toast.makeText(this@MainActivity, "下载失败", Toast.LENGTH_SHORT).show() }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return MediaSniffingManager(this@MainActivity).handleInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                removeFailureView(); bottomNavBar.visibility = View.VISIBLE; adjustFabPosition(true)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request != null && request.isForMainFrame) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val errorCode = error?.errorCode
                        if (errorCode == ERROR_HOST_LOOKUP || errorCode == ERROR_CONNECT || errorCode == ERROR_TIMEOUT) {
                            showFailureView(request.url.toString())
                        }
                    } else showFailureView(request.url.toString())
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                super.onReceivedHttpError(view, request, errorResponse)
                if (request != null && request.isForMainFrame) {
                    val statusCode = errorResponse?.statusCode
                    if (statusCode != null && statusCode >= 400) showFailureView(request.url.toString())
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (url != null && !url.startsWith("about:") && !url.startsWith("data:")) {
                    val title = view?.title ?: ""
                    updateVisitCountAndTitle(url, title)
                    if (layoutMode == "single") activePane = 1 else activePane = paneNum

                    // 标签页更新逻辑
                    val activeTab = TabManager.openTabs.find { it.isActive }
                    if (activeTab == null || (activeTab.url != url && activeTab.title != title)) {
                        TabManager.addTab(title, url)
                    } else {
                        TabManager.updateCurrentTabInfo(title, url)
                    }

                    if (adBlockEnabled) {
                        val savedCss = if (customCssRules.isNotEmpty()) customCssRules.joinToString(", ") { "$it { display: none !important; }" } else ""
                        val adSelectors = "div[class*=\"banner\"],div[id*=\"banner\"]," +
                                "div[class*=\"popup\"],div[class*=\"ad-\"],div[id*=\"ad-\"]," +
                                "div[class*=\"ad_\"],div[id*=\"ad_\"],div[class*=\"ads\"],div[id*=\"ads\"]," +
                                "div[class*=\"sponsor\"],div[id*=\"sponsor\"]," +
                                "iframe[src*=\"ads\"],iframe[src*=\"doubleclick\"],iframe[src*=\"googlesyndication\"]," +
                                "ins.adsbygoogle, .ad-container, #ad-container, .ad-wrapper, #ad-wrapper"
                        view?.evaluateJavascript("(function(){var s=document.createElement('style');s.innerHTML='$adSelectors{$savedCss}{display:none!important;}';document.head.appendChild(s);})();", null)
                    }
                    view?.evaluateJavascript("(function(){if(document.activeElement){document.activeElement.blur();}})();", null)
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view?.windowToken, 0)
                }
            }

            override fun onRenderProcessGone(view: WebView?, detail: android.webkit.RenderProcessGoneDetail?): Boolean {
                Toast.makeText(this@MainActivity, "页面崩溃，正在重建...", Toast.LENGTH_SHORT).show()
                webView1?.destroy(); webView1 = createWebView(1)
                webView2?.destroy(); webView2 = createWebView(2)
                switchLayout(layoutMode); return true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) return false

                if (url.startsWith("intent://") || url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("sms:") || url.startsWith("alipays://") || url.startsWith("weixin://") || url.startsWith("bilibili://")) {
                    try {
                        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                        val now = System.currentTimeMillis()
                        if (now - lastClickTime > 2000) {
                            lastClickTime = now; startActivity(intent)
                        }
                    } catch (e: Exception) {
                        if (url.contains("bilibili://video/")) {
                            val bvid = url.substringAfter("bilibili://video/").substringBefore("?")
                            view?.loadUrl("https://www.bilibili.com/video/$bvid")
                        } else {
                            Toast.makeText(this@MainActivity, "未安装对应 App", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }
                if (url == "about:blank" || url == "javascript:void(0)" || url == "javascript:;") return true
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                val isCurrentWebView = if (activePane == 1) view == webView1 else view == webView2
                if (isCurrentWebView) {
                    if (newProgress == 100) webProgressBar.visibility = View.GONE
                    else { webProgressBar.visibility = View.VISIBLE; webProgressBar.progress = newProgress }
                }
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) { callback?.onCustomViewHidden(); return }
                customView = view; customViewCallback = callback
                window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                topPanel.visibility = View.GONE; previewContainer.visibility = View.VISIBLE
                bottomNavBar.visibility = View.GONE; findViewById<View>(R.id.fabContainer)?.visibility = View.GONE
                findViewById<View>(R.id.leftFabContainer)?.visibility = View.GONE; findViewById<View>(R.id.sidebarToggle)?.visibility = View.GONE
                previewContainer.addView(customView, FrameLayout.LayoutParams(-1, -1))
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }

            override fun onHideCustomView() {
                window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (customView != null) { previewContainer.removeView(customView); customView = null }
                customViewCallback?.onCustomViewHidden(); customViewCallback = null
                topPanel.visibility = View.VISIBLE; bottomNavBar.visibility = View.VISIBLE
                findViewById<View>(R.id.fabContainer)?.visibility = View.VISIBLE
                findViewById<View>(R.id.leftFabContainer)?.visibility = View.VISIBLE
                findViewById<View>(R.id.sidebarToggle)?.visibility = View.VISIBLE
                Handler(Looper.getMainLooper()).postDelayed({ webView1?.requestLayout(); webView1?.invalidate(); webView2?.requestLayout(); webView2?.invalidate() }, 100)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            override fun onShowFileChooser(webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?, fileChooserParams: FileChooserParams?): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                try {
                    val intent = fileChooserParams?.createIntent()
                    if (intent != null) { fileChooserLauncher.launch(intent); return true }
                } catch (e: Exception) {}
                try {
                    val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
                    fileChooserLauncher.launch(fallbackIntent); return true
                } catch (e: Exception) { this@MainActivity.filePathCallback = null; return false }
            }

            override fun onPermissionRequest(request: PermissionRequest?) { runOnUiThread { request?.let { it.grant(it.resources) } } }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                val now = System.currentTimeMillis()
                if (now - lastAlertTime < 1000) { result?.confirm(); return true }
                lastAlertTime = now
                AlertDialog.Builder(this@MainActivity).setMessage(message).setPositiveButton("确定") { _, _ -> result?.confirm() }.setCancelable(false).show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                val now = System.currentTimeMillis()
                if (now - lastAlertTime < 1000) { result?.confirm(); return true }
                lastAlertTime = now
                AlertDialog.Builder(this@MainActivity).setMessage(message).setPositiveButton("确定") { _, _ -> result?.confirm() }.setNegativeButton("取消") { _, _ -> result?.cancel() }.show()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                if (message == "__BLOCK_CSS__" && !defaultValue.isNullOrBlank()) {
                    val rule = defaultValue.trim()
                    if (!customCssRules.contains(rule)) {
                        customCssRules.add(rule)
                        prefs.edit().putStringSet("custom_css_rules", customCssRules).apply()
                        Toast.makeText(this@MainActivity, "已永久屏蔽！翻页依然有效", Toast.LENGTH_SHORT).show()
                    }
                    result?.confirm("")
                    return true
                }
                val input = EditText(this@MainActivity).apply { setText(defaultValue) }
                AlertDialog.Builder(this@MainActivity).setMessage(message).setView(input).setPositiveButton("确定") { _, _ -> result?.confirm(input.text.toString()) }.setNegativeButton("取消") { _, _ -> result?.cancel() }.show()
                return true
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                if (!isUserGesture) return false
                val newWebView = WebView(this@MainActivity)
                newWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(v: WebView?, u: String?) {
                        if (u != null && !u.startsWith("about:") && !u.startsWith("javascript:")) {
                            (if (activePane == 1) webView1 else webView2)?.loadUrl(u)
                        }
                    }
                }
                val transport = resultMsg?.obj as WebView.WebViewTransport
                transport.webView = newWebView
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                if (activePane != paneNum) {
                    activePane = paneNum
                    if (paneNum == 1) webView2?.onPause() else webView1?.onPause()
                    if (paneNum == 1) webView1?.onResume() else webView2?.onResume()
                }
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(v.windowToken, 0)
            }
            false
        }

        webView.setOnLongClickListener {
            val result = webView.hitTestResult
            val extraUrl = result.extra
            if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                if (extraUrl != null) showLinkLongClickMenu(extraUrl)
                return@setOnLongClickListener true
            }
            if (result.type == WebView.HitTestResult.IMAGE_TYPE || result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                if (extraUrl != null) showImageLongClickMenu(extraUrl)
                return@setOnLongClickListener true
            }
            return@setOnLongClickListener false
        }
        return webView
    }

    private fun adjustFabPosition(isUp: Boolean) {
        val randomBtn = findViewById<View>(R.id.randomBtn)
        val sideToggle = findViewById<View>(R.id.sidebarToggle)
        if (randomBtn != null && sideToggle != null) {
            val rParams = randomBtn.layoutParams as? ViewGroup.MarginLayoutParams
            val sParams = sideToggle.layoutParams as? ViewGroup.MarginLayoutParams
            if (rParams != null && sParams != null) {
                if (isUp) {
                    rParams.bottomMargin = 120
                    sParams.bottomMargin = 120
                } else {
                    rParams.bottomMargin = 50
                    sParams.bottomMargin = 50
                }
                randomBtn.layoutParams = rParams
                sideToggle.layoutParams = sParams
            }
        }
    }

    fun showTabManagerDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0x00000000))

        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.9).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // 标题栏
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, padding)
        }
        val tvTitle = TextView(this).apply {
            text = "网页管理"
            textSize = 20f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvClose = TextView(this).apply {
            text = "关闭"
            textSize = 16f
            setTextColor(Color.parseColor("#ff6d00"))
            setPadding(padding, 0, 0, 0)
            setOnClickListener { dialog.dismiss() }
        }
        titleBar.addView(tvTitle)
        titleBar.addView(tvClose)

        // 列表
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setBackgroundColor(Color.parseColor("#1E1E1E"))
        }

        val tabAdapter = TabListAdapter(TabManager.openTabs).apply {
            onItemClick = { clickedTab ->
                dialog.dismiss()
                TabManager.openTabs.forEach { it.isActive = false }
                clickedTab.isActive = true

                if (clickedTab.url.isNotBlank()) {
                    currentPreviewUrl = clickedTab.url
                    val targetWebView = if (activePane == 1) webView1 else webView2
                    targetWebView?.loadUrl(clickedTab.url)
                }
            }
            onTabRemoved = {
                // 如果全删光了，底下的网页也要跟着清空
                if (TabManager.getTabCount() == 0) {
                    val targetWebView = if (activePane == 1) webView1 else webView2
                    targetWebView?.loadUrl("about:blank")
                    previewPlaceholder.visibility = View.VISIBLE
                    bottomNavBar.visibility = View.GONE
                    currentPreviewUrl = null
                } else {
                    // 如果删掉的是当前看的，系统会自动把最后一个设为激活，我们需要把网页切过去
                    val activeTab = TabManager.openTabs.find { it.isActive }
                    if (activeTab != null && currentPreviewUrl != activeTab.url) {
                        currentPreviewUrl = activeTab.url
                        val targetWebView = if (activePane == 1) webView1 else webView2
                        targetWebView?.loadUrl(activeTab.url)
                    }
                }
            }
        }
        recyclerView.adapter = tabAdapter

        // 底部新建按钮
        val btnNew = TextView(this).apply {
            text = "+ 新建网页"
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#ff6d00"))
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = padding
            }
            setOnClickListener {
                // 核心防呆：分屏模式下禁止新建标签页
                if (layoutMode != "single") {
                    Toast.makeText(this@MainActivity, "分屏模式下不支持新建标签，请先切换到单屏模式", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                dialog.dismiss()

                // 单屏模式下的新建逻辑
                val input = EditText(this@MainActivity).apply {
                    hint = "请输入网址 (如 baidu.com)"
                    setSingleLine(true)
                    setPadding(40, 20, 40, 20)
                }
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("新建网页")
                    .setView(input)
                    .setPositiveButton("打开") { _, _ ->
                        var urlStr = input.text.toString().trim()
                        if (urlStr.isNotEmpty()) {
                            if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                                urlStr = "http://$urlStr"
                            }
                            val targetWebView = if (activePane == 1) webView1 else webView2
                            targetWebView?.loadUrl(urlStr)
                            previewPlaceholder.visibility = View.GONE
                            bottomNavBar.visibility = View.VISIBLE
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }

        container.addView(titleBar)
        container.addView(recyclerView)
        container.addView(btnNew)

        dialog.setContentView(container)
        dialog.show()
    }

    private fun setupListeners() {
        findViewById<TextView>(R.id.btnExitApp)?.setOnClickListener { finish() }
        findViewById<View>(R.id.sidebarToggle)?.setOnClickListener { drawerLayout.openDrawer(GravityCompat.START) }
        findViewById<TextView>(R.id.btnLocalPlayer)?.setOnClickListener {
            val intent = Intent(this, LocalPlayerActivity::class.java)
            intent.putExtra("REFERER_URL", currentPreviewUrl ?: "https://www.baidu.com")
            startActivity(intent)
        }

        // 1. 私密系统
        findViewById<TextView>(R.id.btnSwitchBuiltin)?.let { switchBtn ->
            SettingsManager.setupPrivateSystem(this, switchBtn)
        }

        // 2. 拦截规则、代理、白名单
        findViewById<TextView>(R.id.btnManageCssRules)?.setOnClickListener { SettingsManager.showCssRulesDialog(this) }
        findViewById<TextView>(R.id.btnProxy)?.setOnClickListener { SettingsManager.showProxyDialog(this) }
        findViewById<TextView>(R.id.tvCustomAdRulesBtn)?.setOnClickListener { SettingsManager.showCustomAdRulesDialog(this) }
        findViewById<TextView>(R.id.tvManageWhiteListBtn)?.setOnClickListener { SettingsManager.showManageWhiteListDialog(this) }
        findViewById<TextView>(R.id.tvAddWhiteListBtn)?.setOnClickListener {
            val currentWeb = if (activePane == 1) webView1 else webView2; val url = currentWeb?.url
            if (url.isNullOrEmpty() || url.startsWith("about:")) Toast.makeText(this, "当前没有打开的网页", Toast.LENGTH_SHORT).show()
            else {
                val host = try { Uri.parse(url).host } catch (e: Exception) { null }
                if (host != null) { if (adWhiteList.contains(host)) Toast.makeText(this, "已在白名单", Toast.LENGTH_SHORT).show() else { addToAdWhiteList(host); Toast.makeText(this, "已加入白名单，请刷新", Toast.LENGTH_SHORT).show() } }
            }
        }

        // 3. 屏蔽元素、外观、翻译
        findViewById<TextView>(R.id.tvBlockElementBtn)?.setOnClickListener { SettingsManager.toggleBlockElementMode(this) }
        findViewById<TextView>(R.id.btnAppearance)?.setOnClickListener {
            SettingsManager.showAppearanceDialog(this)
        }
        val btnTranslate = findViewById<TextView>(R.id.btnTranslate)
        btnTranslate?.setOnClickListener { doImmersiveTranslate() }
        btnTranslate?.setOnLongClickListener {
            SettingsManager.showTranslateApiDialog(this)
            true
        }

        // 4. 截图、静音、阅读模式
        findViewById<TextView>(R.id.btnLongScreenshot)?.setOnClickListener {
            val currentWeb = (if (activePane == 1) webView1 else webView2) ?: return@setOnClickListener
            Toast.makeText(this, "正在生成长截图...", Toast.LENGTH_SHORT).show()
            currentWeb.evaluateJavascript("(function(){return document.documentElement.scrollHeight;})();") { result ->
                val height = result?.toFloatOrNull()?.toInt() ?: currentWeb.height
                val bitmap = Bitmap.createBitmap(currentWeb.width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                currentWeb.draw(canvas)
                try {
                    val filename = "WebBox_LongShot_${System.currentTimeMillis()}.jpg"
                    val values = android.content.ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/WebBox")
                        }
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                        Toast.makeText(this, "长截图已保存至相册", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) { Toast.makeText(this, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }

        findViewById<TextView>(R.id.btnMute)?.setOnClickListener {
            isMuted = !isMuted
            val currentWeb = (if (activePane == 1) webView1 else webView2) ?: return@setOnClickListener
            currentWeb.evaluateJavascript("var v=document.querySelectorAll('video,audio'); for(var i=0;i<v.length;i++){v[i].muted=$isMuted;}", null)
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (isMuted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
            Toast.makeText(this, if (isMuted) "网页已静音" else "网页已恢复声音", Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.btnCopyAllText)?.setOnClickListener {
            val text = findViewById<TextView>(R.id.tvReaderContent).text.toString()
            if (text.isNotBlank()) {
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("all_text", text))
                Toast.makeText(this, "已复制全文", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<TextView>(R.id.btnEyeCare)?.setOnClickListener {
            isEyeCareMode = !isEyeCareMode
            val readerOverlay = findViewById<LinearLayout>(R.id.textReaderOverlay)
            val tvReaderContent = findViewById<TextView>(R.id.tvReaderContent)
            if (isEyeCareMode) {
                readerOverlay.setBackgroundColor(Color.parseColor("#C7EDCC"))
                tvReaderContent.setTextColor(Color.parseColor("#333333"))
                Toast.makeText(this, "护眼模式：开", Toast.LENGTH_SHORT).show()
            } else {
                readerOverlay.setBackgroundColor(Color.parseColor("#FA000000"))
                tvReaderContent.setTextColor(Color.WHITE)
                Toast.makeText(this, "护眼模式：关", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. 随机、自动、时间、记事本
        findViewById<TextView>(R.id.randomBtn).setOnClickListener { doRandomJump() }
        findViewById<TextView>(R.id.randomBtn).setOnLongClickListener {
            isRandomBtnFixed = !isRandomBtnFixed
            if (isRandomBtnFixed) Toast.makeText(this, "已固定【随机】按钮", Toast.LENGTH_SHORT).show()
            else Toast.makeText(this, "已解除固定", Toast.LENGTH_SHORT).show()
            resetUiTimer()
            true
        }
        findViewById<TextView>(R.id.fabAutoBtn).setOnClickListener { toggleAutoJump(it as TextView) }
        findViewById<TextView>(R.id.fabTimeBtn).setOnClickListener {
            val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
            val etMin = EditText(this).apply { hint = "最小秒数"; inputType = InputType.TYPE_CLASS_NUMBER; setText(autoMinSec.toString()) }
            val etMax = EditText(this).apply { hint = "最大秒数"; inputType = InputType.TYPE_CLASS_NUMBER; setText(autoMaxSec.toString()) }
            container.addView(TextView(this).apply { text = "设置自动跳转秒数范围:" }); container.addView(etMin); container.addView(etMax)
            AlertDialog.Builder(this).setTitle("时间设置").setView(container).setPositiveButton("保存") { _, _ ->
                autoMinSec = etMin.text.toString().toIntOrNull() ?: 10; autoMaxSec = etMax.text.toString().toIntOrNull() ?: 20
                if (autoMaxSec < autoMinSec) autoMaxSec = autoMinSec
            }.setNegativeButton("取消", null).show()
        }
        findViewById<TextView>(R.id.fabNotepadBtn).setOnClickListener { showNotepadDialog() }

        // 6. 收藏、查找、翻译、四大神器
        findViewById<TextView>(R.id.tvPreviewFavBtn)?.setOnClickListener {
            val currentWeb = if (activePane == 1) webView1 else webView2
            val url = currentPreviewUrl ?: currentWeb?.url
            val title = currentWeb?.title ?: ""
            if (url.isNullOrEmpty() || url.startsWith("about:") || url.startsWith("data:")) Toast.makeText(this, "当前没有打开的网页", Toast.LENGTH_SHORT).show()
            else BookmarkManager.showAddCurrentPageToFavoriteDialog(this, url, title)
        }
        findViewById<TextView>(R.id.tvFindBtn)?.setOnClickListener { showFindDialog() }
        findViewById<TextView>(R.id.btnConvertText)?.setOnClickListener {
            val tv = findViewById<TextView>(R.id.tvReaderContent)
            val text = tv.text.toString()
            if (text.isNotEmpty()) {
                try {
                    val converted = ZhConverterUtil.toTraditional(text)
                    if (converted == text) tv.text = ZhConverterUtil.toSimple(text) else tv.text = converted
                    Toast.makeText(this, "已切换", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) { Toast.makeText(this, "转换失败", Toast.LENGTH_SHORT).show() }
            }
        }
        findViewById<TextView>(R.id.fabExtractImg)?.setOnClickListener { hideUiRunnable.run(); ExtractionManager.extractImage(this, if (activePane == 1) webView1 else webView2) }
        findViewById<TextView>(R.id.fabExtractVideo)?.setOnClickListener { hideUiRunnable.run(); ExtractionManager.extractVideo(this, if (activePane == 1) webView1 else webView2) }
        findViewById<TextView>(R.id.fabExtractLink)?.setOnClickListener { hideUiRunnable.run(); ExtractionManager.extractLinks(this, if (activePane == 1) webView1 else webView2) }
        findViewById<TextView>(R.id.fabExtractText)?.setOnClickListener { hideUiRunnable.run(); ExtractionManager.extractText(this, if (activePane == 1) webView1 else webView2) }

        // 7. 阅读模式按钮
        findViewById<TextView>(R.id.btnCloseTextReader)?.setOnClickListener {
            findViewById<View>(R.id.textReaderOverlay)?.visibility = View.GONE
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
        var currentFontSize = 18f
        findViewById<TextView>(R.id.btnFontSmaller)?.setOnClickListener { if (currentFontSize > 12f) { currentFontSize -= 2f; findViewById<TextView>(R.id.tvReaderContent).textSize = currentFontSize } }
        findViewById<TextView>(R.id.btnFontLarger)?.setOnClickListener { if (currentFontSize < 36f) { currentFontSize += 2f; findViewById<TextView>(R.id.tvReaderContent).textSize = currentFontSize } }
        findViewById<TextView>(R.id.btnPrevPage)?.setOnClickListener { val sv = findViewById<ScrollView>(R.id.readerScrollView); sv.smoothScrollBy(0, -sv.height * 4 / 5) }
        findViewById<TextView>(R.id.btnNextPage)?.setOnClickListener { val sv = findViewById<ScrollView>(R.id.readerScrollView); sv.smoothScrollBy(0, sv.height * 4 / 5) }

        // 8. Checkbox、导出、清空、设置
        findViewById<CheckBox>(R.id.cbUnloadHidden)?.apply { isChecked = unloadHidden; setOnCheckedChangeListener { _, b -> unloadHidden = b; if (b && layoutMode == "single") webView2?.loadUrl("about:blank") } }
        findViewById<CheckBox>(R.id.cbDisableScale)?.apply { isChecked = disableScale; setOnCheckedChangeListener { _, b -> disableScale = b; webView1?.settings?.builtInZoomControls = !b; webView2?.settings?.builtInZoomControls = !b; if (b) setZoom(1.0f) } }
        findViewById<CheckBox>(R.id.cbAdBlock)?.apply { isChecked = adBlockEnabled; setOnCheckedChangeListener { _, b -> adBlockEnabled = b; prefs.edit().putBoolean("ad_block_enabled", b).apply() } }

        findViewById<TextView>(R.id.btnCloseSidebar)?.setOnClickListener { drawerLayout.closeDrawer(GravityCompat.START) }
        findViewById<TextView>(R.id.btnClearLogin)?.setOnClickListener { WebDataManager.showClearOptionsDialog(this, if (activePane == 1) webView1 else webView2, currentPreviewUrl) }

        findViewById<TextView>(R.id.sbExportBtn)?.setOnClickListener {
            AlertDialog.Builder(this).setTitle("选择导出范围").setItems(arrayOf("导出全部", "只导出当前分区: $currentZone")) { _, which -> DataBackupManager(this).exportDataToJson(if (which == 0) null else currentZone) }.setNegativeButton("取消", null).show()
        }
        findViewById<TextView>(R.id.sbImportBtn)?.setOnClickListener { openJsonLauncher.launch(arrayOf("application/json")) }
        findViewById<TextView>(R.id.sbExportBookmarksBtn)?.setOnClickListener { DataBackupManager(this).exportHtmlBookmarks() }
        findViewById<TextView>(R.id.sbImportBookmarksBtn)?.setOnClickListener { openHtmlLauncher.launch(arrayOf("text/html")) }
        findViewById<TextView>(R.id.sbHealthBtn)?.setOnClickListener { showHealthCheck() }
        findViewById<TextView>(R.id.sbStatsBtn)?.setOnClickListener { showStatistics() }
        findViewById<TextView>(R.id.sbStorageBtn)?.setOnClickListener { showStorageInfo() }
        findViewById<TextView>(R.id.sbClearAllBtn)?.setOnClickListener { showClearAllDataDialog() }
        findViewById<TextView>(R.id.sbHelpBtn)?.setOnClickListener { showHelpDialog() }

        findViewById<TextView>(R.id.btnSetAppLock)?.setOnClickListener {
            val input = EditText(this).apply { hint = "输入4位数字密码（留空则取消密码）"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; setText(appLockPassword ?: "") }
            AlertDialog.Builder(this).setTitle("设置应用锁").setView(input).setPositiveButton("保存") { _, _ ->
                val pwd = input.text.toString().trim()
                if (pwd.isNotEmpty() && pwd.length != 4) Toast.makeText(this, "密码必须是4位数字", Toast.LENGTH_SHORT).show()
                else { appLockPassword = if (pwd.isEmpty()) null else pwd; prefs.edit().putString("app_lock_pwd", appLockPassword).apply(); Toast.makeText(this, "设置成功", Toast.LENGTH_SHORT).show() }
            }.setNegativeButton("取消", null).show()
        }
        findViewById<TextView>(R.id.btnSponsor)?.setOnClickListener {
            AlertDialog.Builder(this).setTitle("关于本项目").setMessage("WebBox - 一款纯粹的极客浏览器\n\n版本: 1.0.0\n\n本软件开源且完全免费，无任何商业捆绑。").setPositiveButton("确定", null).show()
        }
        findViewById<TextView>(R.id.btnUnlock)?.setOnClickListener {
            val inputPwd = findViewById<EditText>(R.id.etUnlockPwd).text.toString().trim()
            if (inputPwd == appLockPassword) {
                findViewById<LinearLayout>(R.id.lockOverlay).visibility = View.GONE
                findViewById<EditText>(R.id.etUnlockPwd).text.clear()
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(it.windowToken, 0)
            } else Toast.makeText(this, "密码错误！", Toast.LENGTH_SHORT).show()
        }

        // 9. 搜索与筛选、手势监听
        val websiteInput = findViewById<EditText>(R.id.websiteInput); val saveBtn = findViewById<TextView>(R.id.saveWebsiteBtn)
        saveBtn.setOnClickListener { addWebsite(websiteInput.text.toString().trim()); websiteInput.text.clear() }
        websiteInput.setOnEditorActionListener { _, actionId, _ -> if (actionId == EditorInfo.IME_ACTION_DONE) { saveBtn.performClick(); true } else false }

        findViewById<EditText>(R.id.searchInput).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { searchKeyword = s.toString(); filterAndDisplay() }
            override fun afterTextChanged(s: Editable?) {}
        })
        findViewById<TextView>(R.id.excludeVisitedBtn)?.setOnClickListener { excludeVisitedOnly = !excludeVisitedOnly; it.setBackgroundResource(if (excludeVisitedOnly) R.drawable.bg_tab_active else R.drawable.bg_input); filterAndDisplay() }

        val chips = mapOf(R.id.chipAll to "all", R.id.chipImage to "image", R.id.chipVideo to "video", R.id.chipText to "text", R.id.chipUnvisited to "unvisited")
        chips.forEach { (id, filter) ->
            findViewById<TextView>(id)?.setOnClickListener {
                currentFilter = filter
                chips.keys.forEach { chipId ->
                    val btn = findViewById<TextView>(chipId)
                    if (chipId == id) { btn.setBackgroundResource(R.drawable.bg_tab_active); btn.setTextColor(ContextCompat.getColor(this, R.color.white)) }
                    else { btn.setBackgroundResource(R.drawable.bg_input); btn.setTextColor(ContextCompat.getColor(this, R.color.gold_text)) }
                }
                filterAndDisplay()
            }
        }

        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean { if (!isFullScreenMode) { toggleFullScreen(); return true }; return false }
        })
        val hDivider = findViewById<View>(R.id.hDivider); var initialY = 0f; var initialWeight = 58f
        hDivider.setOnTouchListener { _, event ->
            if (isFullScreenMode) {
                if (event.action == MotionEvent.ACTION_DOWN) initialY = event.rawY
                else if (event.action == MotionEvent.ACTION_UP) { if (Math.abs(event.rawY - initialY) < 50) toggleFullScreen() }
                return@setOnTouchListener true
            }
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { initialY = event.rawY; initialWeight = (topPanel.layoutParams as LinearLayout.LayoutParams).weight; true }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = event.rawY - initialY; val screenHeight = resources.displayMetrics.heightPixels.toFloat()
                    var newWeight = initialWeight + (deltaY / screenHeight) * 100f
                    if (newWeight < 20f) newWeight = 20f; if (newWeight > 80f) newWeight = 80f
                    (topPanel.layoutParams as LinearLayout.LayoutParams).weight = newWeight; (bottomPanel.layoutParams as LinearLayout.LayoutParams).weight = 100f - newWeight
                    topPanel.requestLayout(); bottomPanel.requestLayout(); true
                }
                else -> false
            }
        }

        // 10. 底部导航栏操作
        adapter.onItemClick = { val ct = System.currentTimeMillis(); if (ct - lastClickTime > 500) { lastClickTime = ct; openUrlInPreview(it.url) } }
        adapter.onItemLongClick = { view, website -> BookmarkManager.showItemLongClickMenu(this, view, website) }
        adapter.onCheckChanged = { website, isChecked -> if (isChecked) selectedUrls.add(website.url) else selectedUrls.remove(website.url); infoBar.text = "已选中 ${selectedUrls.size} 个" }

        findViewById<TextView>(R.id.selectModeBtn)?.setOnClickListener { toggleSelectMode() }
        findViewById<TextView>(R.id.btnLayoutSingle)?.setOnClickListener { switchLayout("single") }
        findViewById<TextView>(R.id.btnLayoutSplitV)?.setOnClickListener { switchLayout("split-v") }
        findViewById<TextView>(R.id.btnLayoutSplitH)?.setOnClickListener { switchLayout("split-h") }
        findViewById<TextView>(R.id.btnZoomIn)?.setOnClickListener { if (!disableScale) setZoom(sharedZoom + 0.1f) }
        findViewById<TextView>(R.id.btnZoomOut)?.setOnClickListener { if (!disableScale) setZoom(sharedZoom - 0.1f) }
        findViewById<TextView>(R.id.btnZoomReset)?.setOnClickListener { if (!disableScale) setZoom(1.0f) }

        // 替换页面管理
        findViewById<TextView>(R.id.btnRelease)?.apply {
            text = "页面管理"
            setOnClickListener { showTabManagerDialog() }
        }

        findViewById<TextView>(R.id.btnFloatWindow)?.setOnClickListener {
            val existingFloat = findViewById<ViewGroup>(R.id.mainRoot)?.findViewWithTag<View>(floatTag)
            if (existingFloat != null) { findViewById<ViewGroup>(R.id.mainRoot)?.removeView(existingFloat); Toast.makeText(this, "浮动窗口已关闭", Toast.LENGTH_SHORT).show() }
            else FloatingWindowManager.createFloatingWindow(this)
        }

        val activeWebView = { if (activePane == 1) webView1 else webView2 }
        findViewById<TextView>(R.id.btnGoBack)?.setOnClickListener {
            val currentWeb = activeWebView()
            if (currentWeb?.url == "about:blank" || (currentWeb?.url?.startsWith("data:") == true)) {
                if (currentWeb.canGoBack()) currentWeb.goBack() else currentWeb.loadUrl("about:blank")
                previewPlaceholder.visibility = View.VISIBLE; bottomNavBar.visibility = View.GONE; adjustFabPosition(false)
            } else if (currentWeb?.canGoBack() == true) {
                currentWeb.goBack()
            } else {
                currentWeb?.loadUrl("about:blank"); previewPlaceholder.visibility = View.VISIBLE
                bottomNavBar.visibility = View.GONE; adjustFabPosition(false); loadCustomBackground()
            }
        }
        findViewById<TextView>(R.id.btnRefresh)?.setOnClickListener { activeWebView()?.reload() }
        findViewById<TextView>(R.id.btnToggleFab)?.setOnClickListener {
            val isPanelVisible = findViewById<View>(R.id.fabContainer)?.visibility == View.VISIBLE
            if (isPanelVisible) {
                uiHandler.removeCallbacks(hideUiRunnable)
                hideUiRunnable.run()
            } else {
                resetUiTimer()
            }
        }
    }

    fun performSystemSwitch() {
        isPrivateMode = !isPrivateMode
        prefs.edit().putBoolean("is_private_mode", isPrivateMode).apply()

        AppDatabase.closeDb()
        val dbName = if (isPrivateMode) "private_system.db" else "public_system.db"
        db = AppDatabase.getDatabase(this, dbName)

        searchKeyword = ""
        findViewById<EditText>(R.id.searchInput).setText("")
        excludeVisitedOnly = false
        findViewById<TextView>(R.id.excludeVisitedBtn).setBackgroundResource(R.drawable.bg_input)

        currentFilter = "all"
        currentZone = "默认分区"

        webView1?.stopLoading()
        webView2?.stopLoading()
        webView1?.loadUrl("about:blank")
        webView2?.loadUrl("about:blank")
        previewPlaceholder.visibility = View.VISIBLE
        bottomNavBar.visibility = View.GONE
        adjustFabPosition(false)
        currentPreviewUrl = null

        isRandomBtnFixed = false
        findViewById<ViewGroup>(R.id.fabContainer)?.let { c -> for (i in 0 until c.childCount) c.getChildAt(i).visibility = View.VISIBLE }
        findViewById<View>(R.id.sidebarToggle)?.visibility = View.VISIBLE
        findViewById<View>(R.id.leftFabContainer)?.visibility = View.VISIBLE
        resetUiTimer()

        findViewById<TextView>(R.id.btnSwitchBuiltin)?.text = if (isPrivateMode) "当前: 私密系统" else "当前: 公开系统"

        loadData()
        drawerLayout.closeDrawer(GravityCompat.START)
        Toast.makeText(this, if (isPrivateMode) "已进入私密系统" else "已切换回公开系统", Toast.LENGTH_SHORT).show()
    }

    fun showMediaExtractDialog(isVideo: Boolean) {
        hideUiRunnable.run()
        val currentWeb = (if (activePane == 1) webView1 else webView2) ?: return
        val queryTag = "video, source, a"
        val extractJs = """
    (function() {
        var urls = [];
        var nodes = document.querySelectorAll('$queryTag');
        var pageTitle = document.title || "网页媒体";
        for (var i = 0; i < nodes.length; i++) {
            var node = nodes[i];
            var src = node.src || node.href || '';
            if (node.querySelector('source')) { src = node.querySelector('source').src; }
            if (src) {
                if (src.startsWith('//')) src = 'https:' + src;
                if (src.startsWith('http') && (src.match(/\.(mp4|m3u8|flv|avi|mkv)/i) || src.indexOf("mime=video") > -1)) { 
                    urls.push(src + "|" + "" + "|" + pageTitle); 
                }
            }
        }
        return urls.join('\n');
    })();
""".trimIndent()

        currentWeb.evaluateJavascript(extractJs) { result ->
            val jsUrls = mutableListOf<String>()
            if (result != null && result != "null") {
                jsUrls.addAll(result.trim('"').replace("\\n", "\n").split("\n").map { it.trim() }.filter { it.isNotEmpty() })
            }
            val allData = mutableListOf<Triple<String, String, String>>()

            jsUrls.forEach { item ->
                val parts = item.split("|")
                if (parts.isNotEmpty() && parts[0].isNotEmpty()) {
                    allData.add(Triple(parts[0], "", "网页视频"))
                }
            }

            sniffedVideoUrls.forEach { u ->
                if (allData.none { it.first == u }) allData.add(Triple(u, "", "底层视频流"))
            }

            if (allData.isEmpty()) {
                Toast.makeText(this, "未发现视频，请先在网页里点一下播放", Toast.LENGTH_SHORT).show()
            } else {
                val dialog = android.app.Dialog(this)
                val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xEE000000.toInt()); setPadding(30, 30, 30, 30) }
                val titleTv = TextView(this).apply { text = "共发现 ${allData.size} 个视频"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gold_text)); textSize = 18f; setPadding(0, 0, 0, 20) }

                val rv = RecyclerView(this).apply {
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    adapter = SniffedVideoAdapter(allData).also { a ->
                        a.onItemClick = { clickedUrl ->
                            dialog.dismiss()
                            showVideoActionDialog(clickedUrl)
                        }
                    }
                }
                val btnClose = TextView(this).apply { text = "关闭"; setTextColor(ContextCompat.getColor(this@MainActivity, R.color.pink_dark)); gravity = Gravity.CENTER; setPadding(0, 20, 0, 20); textSize = 16f; setOnClickListener { dialog.dismiss() } }

                container.addView(titleTv)
                container.addView(rv, LinearLayout.LayoutParams(-1, 0, 1f))
                container.addView(btnClose)

                dialog.setContentView(container)
                dialog.window?.setLayout(-1, -1)
                dialog.show()
            }
        }
    }

    private fun showNotepadDialog() {
        NotepadDialog(this).show()
    }

    private fun doImmersiveTranslate() {
        val currentWeb = (if (activePane == 1) webView1 else webView2) ?: return
        Toast.makeText(this, "正在翻译...", Toast.LENGTH_SHORT).show()

        val extractJs = """
        (function() {
            var nodes = document.querySelectorAll('p, h1, h2, h3, h4, h5, h6, li');
            var texts = [];
            for (var i = 0; i < nodes.length; i++) {
                var txt = nodes[i].innerText.trim();
                if (txt.length > 5 && !nodes[i].hasAttribute('data-translated')) {
                    nodes[i].setAttribute('data-translated', 'true');
                    texts.push(txt);
                }
            }
            return JSON.stringify(texts);
        })();
    """.trimIndent()

        currentWeb.evaluateJavascript(extractJs) { result ->
            if (result == null || result == "null") return@evaluateJavascript
            try {
                val cleanResult = result.replace("\\\"", "\"").removeSurrounding("\"")
                val jsonArray = JSONArray(cleanResult)
                if (jsonArray.length() == 0) { Toast.makeText(this, "没有可翻译的内容或已翻译", Toast.LENGTH_SHORT).show(); return@evaluateJavascript }

                val testText = jsonArray.getString(0)
                val apiUrl = if (translateApiUrl.isNotBlank()) translateApiUrl else "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q={text}"
                val finalUrl = apiUrl.replace("{text}", Uri.encode(testText)) + if (translateApiKey.isNotBlank() && !apiUrl.contains("key=")) "&key=$translateApiKey" else ""

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val conn = URL(finalUrl).openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "GET"; conn.connectTimeout = 5000; conn.readTimeout = 5000
                        val response = conn.inputStream.bufferedReader().readText()
                        withContext(Dispatchers.Main) {
                            var translated = ""
                            try {
                                val arr = JSONArray(response)
                                translated = arr.getJSONArray(0).getJSONArray(0).getString(0)
                            } catch (e: Exception) { translated = response }
                            val injectJs = """
                            var p = document.querySelectorAll('[data-translated="true"]');
                            if(p.length > 0) {
                                var div = document.createElement('div');
                                div.style.cssText = 'color:#E0E0E0;font-size:0.9em;margin-top:5px;padding-left:10px;border-left:2px solid #E0E0E0;';
                                div.innerText = '${translated.replace("'", "\\'").replace("\n", " ")}';
                                p[0].appendChild(div);
                            }
                        """.trimIndent()
                            currentWeb.evaluateJavascript(injectJs, null)
                            Toast.makeText(this@MainActivity, "翻译成功，已注入演示", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "翻译接口请求失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                    }
                }
            } catch (e: Exception) { Toast.makeText(this, "解析失败", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun showFindDialog() {
        val currentWeb = (if (activePane == 1) webView1 else webView2) ?: return
        val themeColor = ContextCompat.getColor(this, R.color.pink_accent)
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40); background = GradientDrawable().apply { setColor(0x33000000.toInt()); cornerRadius = 30f } }
        val etFind = EditText(this).apply { hint = "输入要查找的内容..."; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); backgroundTintList = android.content.res.ColorStateList.valueOf(themeColor) }
        val tvResult = TextView(this).apply { setPadding(0, 20, 0, 30); setTextColor(themeColor); text = "输入后自动查找"; textSize = 12f }
        val btnLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        val btnPrev = TextView(this).apply { text = " 上一个 "; setTextColor(Color.WHITE); setPadding(30, 15, 30, 15); setBackgroundResource(R.drawable.bg_input) }
        val btnNext = TextView(this).apply { text = " 下一个 "; setTextColor(Color.WHITE); setPadding(30, 15, 30, 15); setBackgroundResource(R.drawable.bg_input); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = 20 } }
        val btnClose = TextView(this).apply { text = " 关闭 "; setTextColor(themeColor); setPadding(30, 15, 30, 15); setBackgroundResource(R.drawable.bg_input); layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = 20 } }
        btnLayout.addView(btnPrev); btnLayout.addView(btnNext); btnLayout.addView(btnClose)
        container.addView(etFind); container.addView(tvResult); container.addView(btnLayout)
        val dialog = AlertDialog.Builder(this).setView(container).create()

        currentWeb.setFindListener { activeMatchOrdinal, numberOfMatches, isDoneCounting ->
            if (numberOfMatches > 0) { tvResult.text = "找到 ${numberOfMatches} 个结果，当前第 ${activeMatchOrdinal + 1} 个"; tvResult.setTextColor(themeColor) }
            else if (isDoneCounting) { tvResult.text = "未找到匹配结果"; tvResult.setTextColor(themeColor) }
        }

        etFind.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { if (s.toString().isNotEmpty()) currentWeb.findAllAsync(s.toString()) else { currentWeb.clearMatches(); tvResult.text = "输入后自动查找" } }
        })
        btnNext.setOnClickListener { currentWeb.findNext(true) }
        btnPrev.setOnClickListener { currentWeb.findNext(false) }
        btnClose.setOnClickListener { currentWeb.clearMatches(); dialog.dismiss() }
        dialog.setOnDismissListener { currentWeb.clearMatches() }
        dialog.show()
        dialog.window?.let { window -> window.setGravity(Gravity.TOP); window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0x00000000)); val lp = window.attributes; lp.width = -1; lp.y = 100; window.attributes = lp }
    }

    private fun switchLayout(mode: String) {
        layoutMode = mode
        previewContainer.removeAllViews()
        (webView1?.parent as? ViewGroup)?.removeView(webView1)
        (webView2?.parent as? ViewGroup)?.removeView(webView2)
        (previewPlaceholder.parent as? ViewGroup)?.removeView(previewPlaceholder)

        val lpV = LinearLayout.LayoutParams(0, -1, 1f)
        val lpH = LinearLayout.LayoutParams(-1, 0, 1f)
        val lpSingle = FrameLayout.LayoutParams(-1, -1)

        when (mode) {
            "single" -> {
                webView1?.layoutParams = lpSingle
                previewContainer.addView(webView1)
                if (!unloadHidden) webView2?.onPause()
                if (!unloadHidden) webView1?.onResume()
                if (unloadHidden) webView2?.loadUrl("about:blank")
                if (webView1?.url.isNullOrEmpty() || webView1?.url == "about:blank") {
                    previewContainer.addView(previewPlaceholder)
                    previewPlaceholder.visibility = View.VISIBLE
                } else {
                    previewPlaceholder.visibility = View.GONE
                }
            }
            "split-v" -> {
                val s = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                webView1?.layoutParams = lpV
                webView2?.layoutParams = lpV
                s.addView(webView1)
                s.addView(webView2)
                previewContainer.addView(s)
            }
            "split-h" -> {
                val s = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                webView1?.layoutParams = lpH
                webView2?.layoutParams = lpH
                s.addView(webView1)
                s.addView(webView2)
                previewContainer.addView(s)
            }
        }
        webView1?.requestLayout(); webView1?.invalidate()
        webView2?.requestLayout(); webView2?.invalidate()
        uiHandler.postDelayed({ webView1?.requestLayout(); webView1?.invalidate(); webView2?.requestLayout(); webView2?.invalidate() }, 200)

        val btnSingle = findViewById<TextView>(R.id.btnLayoutSingle)
        val btnSplitV = findViewById<TextView>(R.id.btnLayoutSplitV)
        val btnSplitH = findViewById<TextView>(R.id.btnLayoutSplitH)
        val activeTextColor = ContextCompat.getColor(this, R.color.white)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.gold_text)

        btnSingle?.setBackgroundResource(if (mode == "single") R.drawable.bg_tab_active else R.drawable.bg_input)
        btnSingle?.setTextColor(if (mode == "single") activeTextColor else inactiveTextColor)
        btnSplitV?.setBackgroundResource(if (mode == "split-v") R.drawable.bg_tab_active else R.drawable.bg_input)
        btnSplitV?.setTextColor(if (mode == "split-v") activeTextColor else inactiveTextColor)
        btnSplitH?.setBackgroundResource(if (mode == "split-h") R.drawable.bg_tab_active else R.drawable.bg_input)
        btnSplitH?.setTextColor(if (mode == "split-h") activeTextColor else inactiveTextColor)
    }

    private fun setZoom(zoom: Float) {
        sharedZoom = zoom.coerceIn(0.3f, 3.0f)
        webView1?.settings?.textZoom = (sharedZoom * 100).toInt()
        webView2?.settings?.textZoom = (sharedZoom * 100).toInt()
        findViewById<TextView>(R.id.tvZoomLabel).text = "${(sharedZoom * 100).toInt()}%"
    }

    private fun showFailureView(url: String) {
        if (failureView != null) return
        failureView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            val tv = TextView(this@MainActivity).apply { text = "网页加载失败\n$url"; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
            val btnRetry = TextView(this@MainActivity).apply { text = "重试"; setTextColor(Color.WHITE); setPadding(30, 20, 30, 20); setOnClickListener { removeFailureView(); if (activePane == 1) webView1?.loadUrl(url) else webView2?.loadUrl(url) } }
            val btnBrowser = TextView(this@MainActivity).apply { text = "用浏览器打开"; setTextColor(Color.WHITE); setPadding(30, 20, 30, 20); setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
            addView(tv); addView(btnRetry); addView(btnBrowser)
        }
        previewContainer.addView(failureView)
    }

    private fun removeFailureView() {
        previewContainer.removeView(failureView)
        failureView = null
    }

    private fun releaseAllWebViews() {
        webView1?.loadUrl("about:blank")
        webView2?.loadUrl("about:blank")
        (webView1?.parent as? ViewGroup)?.removeView(webView1)
        (webView2?.parent as? ViewGroup)?.removeView(webView2)
        (previewPlaceholder.parent as? ViewGroup)?.removeView(previewPlaceholder)
        findViewById<ViewGroup>(R.id.mainRoot)?.findViewWithTag<View>(floatTag)?.let { (it.parent as? ViewGroup)?.removeView(it) }
        previewContainer.removeAllViews()
        previewContainer.addView(webView1)
        previewContainer.addView(previewPlaceholder)
        previewPlaceholder.visibility = View.VISIBLE
        bottomNavBar.visibility = View.GONE
        adjustFabPosition(false)
        loadCustomBackground()
    }

    private fun showHealthCheck() {
        lifecycleScope.launch {
            val allWebsites = db.appDao().getAllWebsites()
            val duplicates = mutableListOf<String>()
            val seen = mutableSetOf<String>()
            allWebsites.forEach { if (!seen.add(it.url)) duplicates.add(it.url) }
            val unvisited = allWebsites.count { it.visitCount == 0 }
            val msg = "【重复收藏】\n${if (duplicates.isEmpty()) "无" else duplicates.joinToString("\n")}\n\n【从未访问】\n共 $unvisited 个网址从未打开过。"
            AlertDialog.Builder(this@MainActivity).setTitle("收藏体检").setMessage(msg).setPositiveButton("确定", null).show()
        }
    }

    private fun showStatistics() {
        lifecycleScope.launch {
            val allWebsites = db.appDao().getAllWebsites()
            val zonesCount = db.appDao().getAllZones().size
            val thisWeek = allWebsites.count { it.createdAt > System.currentTimeMillis() - 7 * 86400000L }
            val msg = "总收藏：${allWebsites.size}\n分区数：$zonesCount\n本周新增：$thisWeek"
            AlertDialog.Builder(this@MainActivity).setTitle("收藏统计").setMessage(msg).setPositiveButton("确定", null).show()
        }
    }

    private fun showStorageInfo() {
        val size = try { String.format("%.2f MB", getDatabasePath(if (isPrivateMode) "private_system.db" else "public_system.db").length() / 1024.0 / 1024.0) } catch (e: Exception) { "未知" }
        AlertDialog.Builder(this).setMessage("当前系统：${if (isPrivateMode) "私密" else "公开"}\n数据库大小：$size\n数据存在本设备，请定期备份。").setPositiveButton("确定", null).show()
    }

    private fun showClearAllDataDialog() {
        AlertDialog.Builder(this).setTitle("危险操作").setMessage("清空当前系统所有收藏、分区、统计数据？此操作不可逆！")
            .setPositiveButton("确认清空") { _, _ ->
                lifecycleScope.launch {
                    db.appDao().getAllWebsites().forEach { db.appDao().deleteWebsite(it) }
                    db.appDao().getAllZones().forEach { if (!it.isDefault) db.appDao().deleteZone(it.name) }
                    Toast.makeText(this@MainActivity, "已清空", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }.setNegativeButton("取消", null).show()
    }

    private fun showVideoActionDialog(videoUrl: String, playlist: ArrayList<String> = arrayListOf()) {
        val options = arrayOf("使用内置播放器", "下载视频", "复制链接", "系统播放器", "浏览器")
        AlertDialog.Builder(this).setTitle("视频操作").setItems(options) { _, which ->
            when (which) {
                0 -> {
                    try {
                        val intent = Intent(this, LocalPlayerActivity::class.java)
                        intent.putExtra("VIDEO_URL", videoUrl)
                        intent.putExtra("REFERER_URL", currentPreviewUrl ?: "https://www.baidu.com")
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "播放器启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                1 -> {
                    try {
                        val request = DownloadManager.Request(Uri.parse(videoUrl))
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        val referer = currentPreviewUrl ?: "https://www.baidu.com"
                        request.addRequestHeader("Referer", referer)
                        var fileName = URLUtil.guessFileName(videoUrl, null, null)
                        if (fileName.isBlank() || fileName == "unknown") {
                            fileName = "WebBox_Video_${System.currentTimeMillis()}.mp4"
                        }
                        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        dm.enqueue(request)
                        Toast.makeText(this, "已加入下载队列，请在通知栏查看进度", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                2 -> {
                    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("video", videoUrl))
                    Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
                }
                3 -> try { startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(Uri.parse(videoUrl), "video/*") }) } catch (e: Exception) { Toast.makeText(this, "无系统播放器", Toast.LENGTH_SHORT).show() }
                4 -> try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))) } catch (e: Exception) {}
            }
        }.setNegativeButton("取消", null).show()
    }
    private fun showHelpDialog() {
        val helpText = "【操作说明】\n- 左侧四大提取神器（文本、图片、视频、音频）\n- 右侧有自动、时间、记事本和随机跳转\n- 阅读模式支持繁简切换、字号调节、护眼模式、一键复制\n- 翻译功能：长按顶栏“翻译”键配置您的自定义API\n- 内置播放器：侧边栏点击进入\n- 长截图：侧边栏点击\n- 屏蔽元素：顶栏点击\n- 内置网址切换：侧边栏点击\n- 双系统切换：侧边栏长按设密码，单击输入密码切换"
        AlertDialog.Builder(this).setTitle("使用说明").setMessage(helpText).setPositiveButton("知道了", null).show()
    }



    private fun showLinkLongClickMenu(url: String) {
        val options = arrayOf("复制链接", "当前窗打开", "新窗打开", "浏览器打开")
        AlertDialog.Builder(this).setTitle(url).setItems(options) { _, which ->
            when (which) {
                0 -> { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", url)); Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show() }
                1 -> openUrlInPreview(url)
                2 -> { activePane = if (activePane == 1) 2 else 1; openUrlInPreview(url) }
                3 -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }.show()
    }

    private fun showImageLongClickMenu(imageUrl: String) {
        val options = arrayOf("保存图片", "复制图片地址", "新窗打开")
        AlertDialog.Builder(this).setItems(options) { _, which ->
            when (which) {
                0 -> {
                    val request = DownloadManager.Request(Uri.parse(imageUrl))
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "webbox/" + System.currentTimeMillis() + ".jpg");
                    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                    Toast.makeText(this, "已开始保存", Toast.LENGTH_SHORT).show()
                }
                1 -> { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("image", imageUrl)); Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show() }
                2 -> { activePane = if (activePane == 1) 2 else 1; openUrlInPreview(imageUrl) }
            }
        }.show()
    }

    fun toggleSelectMode() {
        isSelectMode = !isSelectMode
        selectedUrls.clear()
        if (!isSelectMode) hideBatchBar() else showBatchBar()
        filterAndDisplay()
    }

    private fun showBatchBar() {
        if (batchBar != null) return
        batchBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.black_bg))
            setPadding(20, 20, 20, 20)
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply { gravity = Gravity.BOTTOM }
        }
        val btnSelectAll = TextView(this).apply { text = "全选"; setTextColor(Color.WHITE); setPadding(20, 0, 20, 0); setOnClickListener { allWebsitesInZone.forEach { selectedUrls.add(it.url) }; filterAndDisplay() } }
        val btnMove = TextView(this).apply { text = "移动"; setTextColor(Color.WHITE); setPadding(20, 0, 20, 0); setOnClickListener { BookmarkManager.showBatchMoveDialog(this@MainActivity) } }
        val btnDelete = TextView(this).apply { text = "删除"; setTextColor(Color.WHITE); setPadding(20, 0, 20, 0); setOnClickListener { lifecycleScope.launch { allWebsitesInZone.filter { selectedUrls.contains(it.url) }.forEach { db.appDao().deleteWebsite(it) }; toggleSelectMode(); updateBuiltinSites() } } }
        batchBar?.addView(btnSelectAll); batchBar?.addView(btnMove); batchBar?.addView(btnDelete)
        (previewContainer.parent as? ViewGroup)?.addView(batchBar)
    }

    private fun hideBatchBar() {
        (previewContainer.parent as? ViewGroup)?.removeView(batchBar)
        batchBar = null
    }


    @SuppressLint("ClickableViewAccessibility")

    private fun doRandomJump() {
        lifecycleScope.launch {
            val list = db.appDao().getWebsitesInZone(currentZone).toMutableList()
            if (list.isEmpty()) { Toast.makeText(this@MainActivity, "当前分区无网址", Toast.LENGTH_SHORT).show(); return@launch }
            var filtered = list
            if (searchKeyword.isNotEmpty()) filtered = filtered.filter { it.title.contains(searchKeyword, true) || it.url.contains(searchKeyword, true) }.toMutableList()
            if (filtered.isEmpty()) { Toast.makeText(this@MainActivity, "无匹配网页", Toast.LENGTH_SHORT).show(); return@launch }
            val currentPoolUrls = filtered.map { it.url }.toSet()
            val playlistUrls = randomPlaylist.map { it.url }.toSet()
            if (randomPlaylist.isEmpty() || !currentPoolUrls.containsAll(playlistUrls)) {
                randomPlaylist.clear()
                randomPlaylist.addAll(filtered.shuffled())
            }
            val currentWeb = if (activePane == 1) webView1 else webView2
            val currentUrl = currentWeb?.url ?: ""
            var nextItem = randomPlaylist.removeAt(0)
            if (nextItem.url == currentUrl && randomPlaylist.isNotEmpty()) {
                randomPlaylist.add(nextItem)
                nextItem = randomPlaylist.removeAt(0)
            }
            openUrlInPreview(nextItem.url)
        }
    }

    private fun toggleAutoJump(btn: TextView) {
        if (isAutoRunning) {
            autoJob?.cancel(); isAutoRunning = false; btn.text = "自动"
        } else {
            isAutoRunning = true; btn.text = "停止"
            autoJob = lifecycleScope.launch {
                while (isAutoRunning) {
                    doRandomJump()
                    delay(Random.nextInt(autoMinSec, autoMaxSec + 1) * 1000L)
                }
            }
        }
    }



    private fun addWebsite(input: String) {
        if (input.isEmpty()) return
        lifecycleScope.launch {
            var added = 0
            input.split("\\s+".toRegex()).forEach {
                var u = it
                if (!u.startsWith("http")) u = "http://$u"
                if (db.appDao().insertWebsite(WebsiteEntity(url = u, zoneName = currentZone)) != -1L) added++
            }
            Toast.makeText(this@MainActivity, if (added > 0) "已收藏 $added 个" else "已存在", Toast.LENGTH_SHORT).show()
            filterAndDisplay()
            updateBuiltinSites()
        }
    }

    private fun updateVisitCountAndTitle(url: String, title: String) {
        lifecycleScope.launch {
            val item = db.appDao().getAllWebsites().find { url.startsWith(it.url) }
            if (item != null) {
                db.appDao().updateWebsite(item.copy(visitCount = item.visitCount + 1, lastVisit = System.currentTimeMillis(), title = if (item.title.isBlank()) title else item.title))
                filterAndDisplay()
            }
        }
    }

    fun updateBuiltinSites() {
        lifecycleScope.launch {
            sbBuiltinContainer.removeAllViews()
            val latestSites = db.appDao().getWebsitesInZone("默认分区").sortedByDescending { it.createdAt }.take(6)
            if (latestSites.isEmpty()) {
                val tv = TextView(this@MainActivity).apply { text = "暂无内置网站，请添加收藏"; setTextColor(Color.GRAY); setPadding(20, 10, 20, 10) }
                sbBuiltinContainer.addView(tv)
            } else {
                latestSites.forEach { site ->
                    val btn = TextView(this@MainActivity).apply {
                        text = site.title.ifBlank { site.url }
                        setTextColor(Color.WHITE); setBackgroundColor(0x33000000); setPadding(20, 10, 20, 10)
                        setOnClickListener {
                            openUrlInPreview(site.url)
                            drawerLayout.closeDrawer(GravityCompat.START)
                        }
                    }
                    sbBuiltinContainer.addView(btn, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 5 })
                }
            }
        }
    }

    fun loadData() {
        lifecycleScope.launch {
            if (db.appDao().getAllZones().isEmpty()) db.appDao().insertZone(ZoneEntity(name = "默认分区", isDefault = true, isRandomStart = true))
            if (db.appDao().getAllWebsites().isEmpty()) {
                val builtins = if (isPrivateMode) {
                    listOf(
                        WebsiteEntity(url = "https://theporndude.com/zh", zoneName = "默认分区", title = "ThePornDude"),
                        WebsiteEntity(url = "https://hypnotube.com/index.php", zoneName = "默认分区", title = "hypnotube"),
                        WebsiteEntity(url = "https://nhentai.net", zoneName = "默认分区", title = "Nhentai"),
                        WebsiteEntity(url = "https://www.xvideos.com/", zoneName = "默认分区", title = "Xvideos"),
                        WebsiteEntity(url = "https://hgoon.booru.org/", zoneName = "默认分区", title = "HgoonBooru"),
                        WebsiteEntity(url = "https://www.ixxx.com/zh/", zoneName = "默认分区", title = "IXXX"),

                    )
                } else {
                    listOf(
                        WebsiteEntity(url = "https://www.wikipedia.org", zoneName = "默认分区", title = "维基百科"),
                        WebsiteEntity(url = "https://www.cxthhhhh.com/CXT-Lib/SearchAIO/", zoneName = "默认分区", title = "SearchAIO"),
                    )
                }
                builtins.forEach { db.appDao().insertWebsite(it) }
            }
            allZones = db.appDao().getAllZones()
            renderZoneTabs()
            filterAndDisplay()
            updateBuiltinSites()
            loadCustomBackground()
            val lastExit = prefs.getLong("last_exit_time", 0)
            if (System.currentTimeMillis() - lastExit < 86400000L && allWebsitesInZone.isEmpty()) {
                currentZone = prefs.getString("last_zone", "默认分区") ?: "默认分区"
                val savedLayout = prefs.getString("last_layout", "single") ?: "single"
                activePane = prefs.getInt("last_active_pane", 1)
                val url1 = prefs.getString("last_url1", null)
                val url2 = prefs.getString("last_url2", null)
                switchLayout(savedLayout)
                if (!url1.isNullOrEmpty() && !url1.startsWith("about:")) webView1?.loadUrl(url1)
                if (!url2.isNullOrEmpty() && !url2.startsWith("about:")) webView2?.loadUrl(url2)
                if (!url1.isNullOrEmpty() || !url2.isNullOrEmpty()) {
                    previewPlaceholder.visibility = View.GONE
                    bottomNavBar.visibility = View.VISIBLE
                    adjustFabPosition(true)
                }
            } else switchLayout("single")
        }
    }

    fun filterAndDisplay() {
        lifecycleScope.launch {
            allWebsitesInZone = db.appDao().getWebsitesInZone(currentZone)
            var filtered = allWebsitesInZone
            if (excludeVisitedOnly) filtered = filtered.filter { it.visitCount == 0 }
            if (searchKeyword.isNotEmpty()) filtered = filtered.filter {
                it.title.contains(searchKeyword, true) || it.url.contains(searchKeyword, true) || it.note.contains(searchKeyword, true) || it.tags.contains(searchKeyword, true)
            }
            when (currentFilter) {
                "image" -> filtered = filtered.filter { it.url.contains("image", true) || it.url.contains("pic", true) || it.url.contains("photo", true) || it.url.contains("img", true) || it.url.contains("jpg", true) || it.url.contains("png", true) || it.title.contains("图", true) }
                "video" -> filtered = filtered.filter { it.url.contains("video", true) || it.url.contains("play", true) || it.url.contains("watch", true) || it.url.contains("mp4", true) || it.title.contains("视频", true) }
                "audio" -> filtered = filtered.filter { it.url.contains("audio", true) || it.url.contains("music", true) || it.url.contains("mp3", true) || it.title.contains("音", true) }
                "text" -> filtered = filtered.filter { it.url.contains("text", true) || it.url.contains("txt", true) || it.url.contains("read", true) || it.title.contains("文", true) }
                "unvisited" -> filtered = filtered.filter { it.visitCount == 0 }
            }
            filtered = filtered.sortedWith(compareByDescending<WebsiteEntity> { it.url == lastVisitedUrl }.thenByDescending { it.lastVisit })
            adapter.updateData(filtered, isSelectMode, selectedUrls)
            infoBar.text = if (isSelectMode) "已选中 ${selectedUrls.size} 个" else {
                if (filtered.isEmpty() && allWebsitesInZone.isNotEmpty()) "当前分类下没有匹配的网页"
                else "你已收藏 ${db.appDao().getTotalCount()} 个网页"
            }
        }
    }

    fun renderZoneTabs() {
        zoneTabsContainer.removeAllViews()
        allZones.forEach { zone ->
            val btn = TextView(this)
            btn.text = if (zone.isRandomStart) "${zone.name} (初始随机)" else zone.name
            btn.textSize = 12f
            btn.setTextColor(if (zone.name == currentZone) ContextCompat.getColor(this, R.color.white) else ContextCompat.getColor(this, R.color.gold_text))
            btn.setBackgroundResource(if (zone.name == currentZone) R.drawable.bg_tab_active else R.drawable.bg_input)
            btn.gravity = Gravity.CENTER; btn.setPadding(30, 0, 30, 0)
            btn.setOnClickListener { currentZone = zone.name; loadData() }
            btn.setOnLongClickListener { BookmarkManager.showZoneMenu(this@MainActivity, zone); true }
            zoneTabsContainer.addView(btn, LinearLayout.LayoutParams(-2, 90).apply { marginEnd = 10 })
        }
        val addBtn = TextView(this)
        addBtn.text = "+ 新建"; addBtn.textSize = 12f
        addBtn.setTextColor(ContextCompat.getColor(this, R.color.pink_accent))
        addBtn.setBackgroundResource(R.drawable.bg_tab_dashed)
        addBtn.gravity = Gravity.CENTER; addBtn.setPadding(30, 0, 30, 0)
        addBtn.setOnClickListener { BookmarkManager.showAddZoneDialog(this) }
        zoneTabsContainer.addView(addBtn, LinearLayout.LayoutParams(-2, 90).apply { marginStart = 10 })
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val inputStream = contentResolver.openInputStream(uri)
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val jsonStr = reader.readText()
                        reader.close()
                        withContext(Dispatchers.Main) {
                            DataBackupManager(this@MainActivity).importDataFromJson(jsonStr)
                            Toast.makeText(this@MainActivity, "已导入数据", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { Toast.makeText(this@MainActivity, "读取失败", Toast.LENGTH_SHORT).show() }
                    }
                }
                intent.action = ""
                return
            }
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrEmpty()) {
                val matcher = android.util.Patterns.WEB_URL.matcher(sharedText)
                if (matcher.find()) {
                    val url = matcher.group()
                    if (url != null) uiHandler.postDelayed({ openUrlInPreview(url); Toast.makeText(this, "已打开网页", Toast.LENGTH_SHORT).show() }, 300)
                } else {
                    findViewById<EditText>(R.id.websiteInput).setText(sharedText)
                    Toast.makeText(this, "已填入文本", Toast.LENGTH_SHORT).show()
                }
            }
            intent.action = ""
        }
    }

    fun openUrlInPreview(url: String) {
        previewPlaceholder.visibility = View.GONE
        bottomNavBar.visibility = View.VISIBLE
        adjustFabPosition(true)
        var finalUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) "http://$url" else url
        if (currentPreviewUrl != null && currentPreviewUrl != finalUrl) lastVisitedUrl = currentPreviewUrl
        currentPreviewUrl = finalUrl
        val targetWebView = if (activePane == 1) webView1 else webView2
        val isImage = url.matches(Regex("(?i).*\\.(jpe?g|png|gif|webp)(\\?|#|$)")) || url.contains("/image", true) || url.contains("/pic", true) || url.contains("/photo", true) || url.contains("/img", true)
        if (isImage) {
            val html = "<html><body style='margin:0;background:#000;display:flex;justify-content:center;align-items:center;height:100vh;'><img src='$finalUrl' style='max-width:100%;max-height:100%;'></body></html>"
            targetWebView?.loadDataWithBaseURL(finalUrl, html, "text/html", "utf-8", null)
        } else if (url.matches(Regex("(?i).*\\.(mp3|wav|m4a|aac|ogg)(\\?|#|$)"))) {
            val html = "<html><body style='margin:0;background:#000;display:flex;justify-content:center;align-items:center;height:100vh;'><audio controls autoplay='false' src='$finalUrl' style='width:80%;'></audio></body></html>"
            targetWebView?.loadDataWithBaseURL(finalUrl, html, "text/html", "utf-8", null)
        } else if (url.matches(Regex("(?i).*\\.(txt|md|json|csv)(\\?|#|$)"))) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val text = URL(finalUrl).readText()
                    val html = "<html><body style='background:#111;color:#ffffff;padding:15px;word-wrap:break-word;white-space:pre-wrap;font-family:monospace;'>$text</body></html>"
                    withContext(Dispatchers.Main) { targetWebView?.loadDataWithBaseURL(finalUrl, html, "text/html", "utf-8", null) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { showFailureView(finalUrl) }
                }
            }
        } else targetWebView?.loadUrl(finalUrl)
    }

    private fun toggleFullScreen() {
        val now = System.currentTimeMillis()
        if (now - lastFullScreenToggleTime < 500) return
        lastFullScreenToggleTime = now
        isFullScreenMode = !isFullScreenMode
        val lpTop = topPanel.layoutParams as LinearLayout.LayoutParams
        val lpBottom = bottomPanel.layoutParams as LinearLayout.LayoutParams
        val hDivider = findViewById<View>(R.id.hDivider)
        val hDividerParams = hDivider.layoutParams as LinearLayout.LayoutParams
        if (isFullScreenMode) {
            topPanel.visibility = View.GONE
            hDivider.visibility = View.VISIBLE
            hDividerParams.height = (40 * resources.displayMetrics.density).toInt()
            hDividerParams.topMargin = (20 * resources.displayMetrics.density).toInt()
            lpTop.weight = 0f; lpBottom.weight = 100f
            Toast.makeText(this, "点击顶部黄条恢复", Toast.LENGTH_SHORT).show()
        } else {
            topPanel.visibility = View.VISIBLE
            hDivider.visibility = View.VISIBLE
            hDividerParams.height = (8 * resources.displayMetrics.density).toInt()
            hDividerParams.topMargin = 0
            lpTop.weight = 58f; lpBottom.weight = 42f
        }
        hDivider.layoutParams = hDividerParams; topPanel.layoutParams = lpTop; bottomPanel.layoutParams = lpBottom; hDivider.requestLayout()
    }

    private fun resetUiTimer() {
        findViewById<View>(R.id.fabContainer)?.let { if (it.visibility != View.VISIBLE && customView == null) { it.visibility = View.VISIBLE; it.animate().alpha(1f).setDuration(300).start() } }
        findViewById<View>(R.id.leftFabContainer)?.let { if (it.visibility != View.VISIBLE && customView == null) { it.visibility = View.VISIBLE; it.animate().alpha(1f).setDuration(300).start() } }
        findViewById<View>(R.id.sidebarToggle)?.let { if (it.visibility != View.VISIBLE && customView == null) { it.visibility = View.VISIBLE; it.animate().alpha(1f).setDuration(300).start() } }
        findViewById<View>(R.id.randomBtn)?.let { if (it.visibility != View.VISIBLE && customView == null) { it.visibility = View.VISIBLE; it.animate().alpha(1f).setDuration(300).start() } }

        uiHandler.removeCallbacks(hideUiRunnable)
        uiHandler.postDelayed(hideUiRunnable, 8000)
    }

    private fun setupBackPressedHandler() {
        val currentWebView = if (activePane == 1) webView1 else webView2
        val currentUrl = currentWebView?.url ?: ""
        if ((currentUrl == "about:blank" || currentUrl.isEmpty()) && currentWebView?.canGoBack() != true) {
            doRandomJump()
            return
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val reader = findViewById<View>(R.id.textReaderOverlay)
                if (reader != null && reader.visibility == View.VISIBLE) { reader.visibility = View.GONE; return }
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) { drawerLayout.closeDrawer(GravityCompat.START); return }
                if (customView != null) { webView1?.let { (it.webChromeClient as? WebChromeClient)?.onHideCustomView() }; return }
                val currentWeb = if (activePane == 1) webView1 else webView2
                val otherWeb = if (activePane == 1) webView2 else webView1
                if (currentWeb?.canGoBack() == true) currentWeb.goBack()
                else if (layoutMode != "single" && otherWeb?.canGoBack() == true) otherWeb.goBack()
                else doRandomJump()
            }
        })
    }

    fun loadCustomAdRules() {
        val customRules = prefs.getString("custom_ad_rules", "") ?: ""
        customAdRuleList.clear()
        if (customRules.isNotBlank()) customAdRuleList.addAll(customRules.split(Regex("[,\\n\\s]+")).map { it.trim().lowercase() }.filter { it.isNotBlank() })
    }

    private fun loadAdWhiteList() {
        val list = prefs.getStringSet("ad_white_list", emptySet()) ?: emptySet()
        adWhiteList.clear(); adWhiteList.addAll(list)
    }

    private fun addToAdWhiteList(host: String) {
        adWhiteList.add(host); prefs.edit().putStringSet("ad_white_list", adWhiteList).apply()
    }
}