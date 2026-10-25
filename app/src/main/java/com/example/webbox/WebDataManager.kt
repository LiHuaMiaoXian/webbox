package com.example.webbox

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.view.ContextThemeWrapper

object WebDataManager {

    fun showClearOptionsDialog(activity: Activity, webView: WebView?, currentUrl: String?) {
        // 强制使用纯白底黑字主题，防止深色模式反转
        val wrapper = ContextThemeWrapper(activity, android.R.style.Theme_Material_Light_Dialog_Alert)

        val options = arrayOf("仅退出当前网站", "彻底清空所有登录状态", "取消")

        AlertDialog.Builder(wrapper)
            .setTitle("清除网页登录状态")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> {
                        // 仅退出当前网站
                        if (currentUrl.isNullOrEmpty() || currentUrl.startsWith("about:") || currentUrl.startsWith("data:")) {
                            Toast.makeText(activity, "当前没有有效网页", Toast.LENGTH_SHORT).show()
                            return@setItems
                        }

                        try {
                            val uri = Uri.parse(currentUrl)
                            val host = uri.host ?: ""

                            // 1. 精准清理当前域名的 Cookie
                            val cookieManager = CookieManager.getInstance()
                            val cookies = cookieManager.getCookie(currentUrl)
                            if (!cookies.isNullOrEmpty()) {
                                val cookieArray = cookies.split("; ")
                                for (cookie in cookieArray) {
                                    val key = cookie.substringBefore("=")
                                    // 构造过期时间强制删除 (Max-Age=0)
                                    cookieManager.setCookie(currentUrl, "$key=; Max-Age=0; Path=/")
                                    if (host.isNotEmpty()) {
                                        cookieManager.setCookie(currentUrl, "$key=; Max-Age=0; Path=/; Domain=$host")
                                        cookieManager.setCookie(currentUrl, "$key=; Max-Age=0; Path=/; Domain=.$host")
                                    }
                                }
                                cookieManager.flush()
                            }

                            // 2. 注入 JS 清理当前网站的 localStorage 和 sessionStorage
                            webView?.evaluateJavascript("javascript:localStorage.clear(); sessionStorage.clear();", null)

                            Toast.makeText(activity, "已清理当前网站状态，请刷新页面", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(activity, "清理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    1 -> {
                        // 彻底清空所有登录状态
                        try {
                            // 清理所有 Cookie
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.removeAllCookies(null)
                            cookieManager.flush()

                            // 清理 HTML5 本地存储 (localStorage, WebSQL, IndexedDB)
                            WebStorage.getInstance().deleteAllData()

                            // 清理网页缓存和历史记录
                            webView?.clearCache(true)
                            webView?.clearHistory()

                            Toast.makeText(activity, "已彻底清空所有登录状态和缓存", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(activity, "清理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                    // 选项 2 是取消，不需要写任何逻辑，AlertDialog 默认会自动关闭
                }
            }
            .show()
    }
}