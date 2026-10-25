package com.example.webbox

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import java.io.ByteArrayInputStream

class MediaSniffingManager(private val activity: MainActivity) {

    // 供外部调用的拦截方法
    fun handleInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        if (request == null) return null
        val originalUrl = request.url?.toString() ?: ""
        val checkUrl = originalUrl.lowercase()
        val path = request.url?.path?.lowercase() ?: ""
        val host = request.url?.host?.lowercase() ?: ""

        // 1. 抓视频（底层嗅探，原封不动）
        if (checkUrl.contains(".mp4") || checkUrl.contains(".m3u8") || checkUrl.contains(".flv") || checkUrl.contains("mime=video")) {
            if (activity.sniffedVideoUrls.size < 200) activity.sniffedVideoUrls.add(originalUrl)
        }

        // 2. 安全放行名单（绝对不能拦主框架、常规静态资源、和视频流）
        if (request.isForMainFrame ||
            path.endsWith(".js") || path.endsWith(".mjs") || path.endsWith(".css") ||
            path.endsWith(".json") || path.endsWith(".woff") || path.endsWith(".woff2") ||
            checkUrl.contains(".mp4") || checkUrl.contains(".m3u8") || checkUrl.contains("bilivideo")) {
            return null
        }

        // 3. 如果广告拦截开关关闭，直接放行
        if (!activity.adBlockEnabled) return null

        // 4. 检查白名单（如果用户把某个站加白了，这个站的所有请求都不拦）
        try {
            for (whiteHost in activity.adWhiteList) {
                if (host.contains(whiteHost)) return null
            }
        } catch (e: Exception) {}

        // 5. 增强版默认广告拦截域名列表
        val defaultAdHosts = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com", "googletagservices.com",
            "adnxs.com", "popads.net", "propellerads.com", "exoclick.com", "juicyads.com",
            "trafficjunky.net", "tsyndicate.com", "taboola.com", "outbrain.com", "bet365.com",
            "1xbet.com", "adserver.", "adservice.", "adsystem.", "adcolony.com", "applovin.com",
            "unityads.unity3d.com", "vungle.com", "chartboost.com", "inmobi.com", "criteo.com",
            "criteo.net", "adroll.com", "pubmatic.com", "rubiconproject.com", "openx.net",
            "yieldmo.com", "sharethrough.com", "3lift.com", "smartadserver.com", "teads.tv",
            "google-analytics.com", "googletagmanager.com", "hm.baidu.com", "cnzz.com",
            "umeng.com", "growingio.com", "sensorsdata.cn", "kuaishou.com/rest/zt", "ads.tiktok.com"
        )
        for (adHost in defaultAdHosts) {
            if (host.contains(adHost)) return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
        }

        // 6. 增强版：拦截常见的广告特征路径
        // 注意：这里不拦 .jpg/.png 等，只拦路径中明确带有广告特征的请求，避免误杀正常图片
        val adPathKeywords = listOf(
            "/ads/", "/adserver/", "/adservice/", "/banner/", "/banners/", "/sponsor/", "/sponsors/",
            "ad_type=", "adzone=", "ad_slot=", "adunit=", "/ad_frame/", "/adimage/",
            "/track/", "/tracker/", "/tracking/", "/analytics/", "/stats/", "/statistics/"
        )
        for (kw in adPathKeywords) {
            if (path.contains(kw) || checkUrl.contains(kw)) {
                // 防止误杀：如果路径里同时带有普通图片后缀，放行
                if (path.endsWith(".jpg") || path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".webp")) {
                    if (path.contains("banner") || path.contains("ad_")) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                    }
                } else {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                }
            }
        }

        // 7. 检查用户自定义拦截规则
        if (activity.customAdRuleList.isNotEmpty()) {
            for (rule in activity.customAdRuleList) {
                if (checkUrl.contains(rule)) return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
            }
        }

        return null // 放行
    }
}