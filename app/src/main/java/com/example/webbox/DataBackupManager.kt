package com.example.webbox

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class DataBackupManager(private val activity: MainActivity) {

    fun exportHtmlBookmarks() {
        activity.lifecycleScope.launch {
            val websites = activity.db.appDao().getAllWebsites()
            val sb = StringBuilder()
            sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n<TITLE>Bookmarks</TITLE>\n<H1>Bookmarks</H1>\n<DL><p>\n")
            websites.groupBy { it.zoneName }.forEach { (zone, list) ->
                sb.append("    <DT><H3>$zone</H3>\n    <DL><p>\n")
                list.forEach { sb.append("        <DT><A HREF=\"${it.url}\">${it.title.ifBlank { it.url }}</A>\n") }
                sb.append("    </DL><p>\n")
            }
            sb.append("</DL><p>\n")
            activity.htmlExportContent = sb.toString()
            activity.createHtmlLauncher.launch("bookmarks_${System.currentTimeMillis()}.html")
        }
    }

    fun importHtmlBookmarks(htmlStr: String) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val regex = Regex("<A HREF=\"(.*?)\".*?>(.*?)</A>", RegexOption.IGNORE_CASE)
                var added = 0
                for (match in regex.findAll(htmlStr)) {
                    val url = match.groupValues[1]
                    val title = match.groupValues[2]
                    if (url.isNotBlank() && !url.startsWith("javascript:")) {
                        if (activity.db.appDao().insertWebsite(WebsiteEntity(url = url, zoneName = "书签导入", title = title)) != -1L) added++
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "书签导入完成，共 $added 条", Toast.LENGTH_SHORT).show()
                    activity.loadData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(activity, "书签解析失败", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun exportDataToJson(targetZone: String? = null) {
        activity.lifecycleScope.launch {
            val allData = if (targetZone == null) activity.db.appDao().getAllWebsites() else activity.db.appDao().getWebsitesInZone(targetZone)
            if (allData.isEmpty()) {
                Toast.makeText(activity, "没有可导出的数据", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val jsonArray = JSONArray()
            allData.forEach {
                jsonArray.put(JSONObject().apply {
                    put("url", it.url)
                    put("zoneName", it.zoneName)
                    put("title", it.title)
                    put("tags", it.tags)
                    put("note", it.note)
                    put("visitCount", it.visitCount)
                    put("lastVisit", it.lastVisit)
                })
            }
            activity.jsonExportContent = jsonArray.toString(2)
            val fileName = if (targetZone == null) "webbox_All_${System.currentTimeMillis()}.json" else "webbox_${targetZone}_${System.currentTimeMillis()}.json"
            activity.createJsonLauncher.launch(fileName)
        }
    }

    fun importDataFromJson(jsonStr: String) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            try {
                var added = 0
                var newZones = 0
                val existingUrls = mutableSetOf<String>()
                activity.db.appDao().getAllWebsites().forEach { existingUrls.add(it.url) }

                if (jsonStr.trim().startsWith("{")) {
                    val rootObj = JSONObject(jsonStr)
                    val zoneDataObj = rootObj.optJSONObject("zoneData")
                    if (zoneDataObj != null) {
                        val keys = zoneDataObj.keys()
                        while (keys.hasNext()) {
                            val zoneName = keys.next()
                            val array = zoneDataObj.getJSONArray(zoneName)
                            if (activity.db.appDao().getAllZones().none { it.name == zoneName }) {
                                activity.db.appDao().insertZone(ZoneEntity(name = zoneName))
                                newZones++
                            }
                            for (i in 0 until array.length()) {
                                val obj = array.getJSONObject(i)
                                val url = obj.optString("url")
                                if (url.isBlank() || existingUrls.contains(url)) continue
                                var tagsStr = ""
                                val tagsArray = obj.optJSONArray("tags")
                                if (tagsArray != null) {
                                    val list = mutableListOf<String>()
                                    for (j in 0 until tagsArray.length()) list.add(tagsArray.getString(j))
                                    tagsStr = list.joinToString(",")
                                }
                                val entity = WebsiteEntity(url = url, zoneName = zoneName, title = obj.optString("title"), tags = tagsStr, note = obj.optString("note"), visitCount = obj.optInt("visitCount", 0), lastVisit = obj.optLong("lastVisit", 0L))
                                if (activity.db.appDao().insertWebsite(entity) != -1L) {
                                    existingUrls.add(url)
                                    added++
                                }
                            }
                        }
                    }
                } else if (jsonStr.trim().startsWith("[")) {
                    val array = JSONArray(jsonStr)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val url = obj.getString("url")
                        val zoneName = obj.optString("zoneName", "默认分区")
                        if (url.isBlank() || existingUrls.contains(url)) continue
                        if (activity.db.appDao().getAllZones().none { it.name == zoneName }) {
                            activity.db.appDao().insertZone(ZoneEntity(name = zoneName))
                            newZones++
                        }
                        val entity = WebsiteEntity(url = url, zoneName = zoneName, title = obj.optString("title", ""), tags = obj.optString("tags", ""), note = obj.optString("note", ""), visitCount = obj.optInt("visitCount", 0), lastVisit = obj.optLong("lastVisit", 0L))
                        if (activity.db.appDao().insertWebsite(entity) != -1L) {
                            existingUrls.add(url)
                            added++
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, "导入完成：新增 $added 个，新分区 $newZones 个", Toast.LENGTH_LONG).show()
                    activity.loadData()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(activity, "解析失败: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}