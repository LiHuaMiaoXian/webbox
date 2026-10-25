package com.example.webbox

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object BookmarkManager {

    // ==========================================
    // 分区管理
    // ==========================================
    fun showAddZoneDialog(activity: MainActivity) {
        val input = EditText(activity).apply { hint = "新分区名称" }
        AlertDialog.Builder(activity).setTitle("新建分区").setView(input).setPositiveButton("创建") { _, _ ->
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) activity.lifecycleScope.launch {
                activity.db.appDao().insertZone(ZoneEntity(name = name))
                activity.loadData()
            }
        }.setNegativeButton("取消", null).show()
    }

    fun showZoneMenu(activity: MainActivity, zone: ZoneEntity) {
        val options = arrayOf("设为启动随机分区", "重命名", if (zone.isDefault) "" else "删除").filter { it.isNotEmpty() }
        AlertDialog.Builder(activity).setTitle("分区：${zone.name}").setItems(options.toTypedArray()) { _, which ->
            when (options[which]) {
                "设为启动随机分区" -> activity.lifecycleScope.launch {
                    activity.db.appDao().clearAllRandomStart()
                    activity.db.appDao().setRandomStart(zone.name)
                    activity.loadData()
                }
                "重命名" -> showRenameZoneDialog(activity, zone)
                "删除" -> activity.lifecycleScope.launch {
                    activity.db.appDao().deleteZone(zone.name)
                    if (activity.currentZone == zone.name) activity.currentZone = "默认分区"
                    activity.loadData()
                }
            }
        }.show()
    }

    fun showRenameZoneDialog(activity: MainActivity, zone: ZoneEntity) {
        val input = EditText(activity).apply { setText(zone.name) }
        AlertDialog.Builder(activity).setTitle("重命名分区").setView(input).setPositiveButton("保存") { _, _ ->
            val newName = input.text.toString().trim()
            if (newName.isNotEmpty() && newName != zone.name) activity.lifecycleScope.launch {
                activity.db.appDao().insertZone(zone.copy(name = newName))
                activity.db.appDao().deleteZone(zone.name)
                activity.loadData()
            }
        }.setNegativeButton("取消", null).show()
    }

    // ==========================================
    // 书签长按菜单
    // ==========================================
    fun showItemLongClickMenu(activity: MainActivity, view: android.view.View, website: WebsiteEntity) {
        val popup = PopupMenu(activity, view)
        popup.menu.add(0, 0, 0, "打开")
        popup.menu.add(0, 1, 0, "用浏览器打开")
        popup.menu.add(0, 3, 0, "编辑")
        popup.menu.add(0, 4, 0, "复制网址")
        popup.menu.add(0, 5, 0, "移动")
        popup.menu.add(0, 8, 0, "删除")
        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                0 -> { activity.openUrlInPreview(website.url); true }
                1 -> { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(website.url))); true }
                3 -> { showEditDialog(activity, website); true }
                4 -> { (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("url", website.url)); Toast.makeText(activity, "已复制", Toast.LENGTH_SHORT).show(); true }
                5 -> { showMoveToZoneDialog(activity, website); true }
                8 -> {
                    activity.lifecycleScope.launch {
                        activity.db.appDao().deleteWebsite(website)
                        activity.filterAndDisplay()
                        activity.updateBuiltinSites()
                    }; true
                }
                else -> false
            }
        }
        popup.show()
    }

    fun showEditDialog(activity: MainActivity, website: WebsiteEntity) {
        val container = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
        val etTitle = EditText(activity).apply { hint = "标题"; setText(website.title) }
        val etTags = EditText(activity).apply { hint = "标签"; setText(website.tags) }
        val etNote = EditText(activity).apply { hint = "备注"; setText(website.note) }
        container.addView(etTitle); container.addView(etTags); container.addView(etNote)
        AlertDialog.Builder(activity).setTitle("编辑").setView(container).setPositiveButton("保存") { _, _ ->
            activity.lifecycleScope.launch {
                activity.db.appDao().updateWebsite(website.copy(title = etTitle.text.toString(), tags = etTags.text.toString(), note = etNote.text.toString()))
                activity.filterAndDisplay()
                activity.updateBuiltinSites()
            }
        }.setNegativeButton("取消", null).show()
    }

    fun showMoveToZoneDialog(activity: MainActivity, website: WebsiteEntity) {
        val zoneNames = activity.allZones.map { it.name }.filter { it != website.zoneName }.toTypedArray()
        if (zoneNames.isEmpty()) { Toast.makeText(activity, "没有其他分区", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(activity).setTitle("移动到").setItems(zoneNames) { _, which ->
            activity.lifecycleScope.launch {
                activity.db.appDao().moveWebsiteToZone(website.url, zoneNames[which])
                activity.filterAndDisplay()
                activity.updateBuiltinSites()
            }
        }.show()
    }

    // ==========================================
    // 批量操作
    // ==========================================
    fun showBatchMoveDialog(activity: MainActivity) {
        val zoneNames = activity.allZones.map { it.name }.filter { it != activity.currentZone }.toTypedArray()
        if (zoneNames.isEmpty()) { Toast.makeText(activity, "没有其他分区", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(activity).setTitle("移动到").setItems(zoneNames) { _, which ->
            activity.lifecycleScope.launch {
                activity.selectedUrls.forEach { url ->
                    activity.db.appDao().moveWebsiteToZone(url, zoneNames[which])
                }
                activity.toggleSelectMode()
                activity.updateBuiltinSites()
            }
        }.show()
    }

    // ==========================================
    // 添加到收藏
    // ==========================================
    fun showAddCurrentPageToFavoriteDialog(activity: MainActivity, url: String, title: String) {
        val container = android.widget.LinearLayout(activity).apply { orientation = android.widget.LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
        val etUrl = EditText(activity).apply { hint = "网址"; setText(url); setSingleLine(true) }
        val etTitle = EditText(activity).apply { hint = "标题"; setText(title); setSingleLine(true) }
        val tvZoneLabel = android.widget.TextView(activity).apply { text = "选择分区："; setTextColor(Color.GRAY); setPadding(0, 30, 0, 10) }
        val zoneNames = activity.allZones.map { it.name }
        val finalZoneNames = if (zoneNames.isEmpty()) listOf("默认分区") else zoneNames
        var selectedZone = if (finalZoneNames.contains(activity.currentZone)) activity.currentZone else finalZoneNames[0]
        val tvZoneValue = android.widget.TextView(activity).apply { text = selectedZone; setTextColor(androidx.core.content.ContextCompat.getColor(activity, R.color.gold_text)); setPadding(0, 10, 0, 10); textSize = 16f }

        tvZoneValue.setOnClickListener {
            AlertDialog.Builder(activity).setTitle("选择分区").setSingleChoiceItems(finalZoneNames.toTypedArray(), finalZoneNames.indexOf(selectedZone)) { dialog, which ->
                selectedZone = finalZoneNames[which]; tvZoneValue.text = selectedZone; dialog.dismiss()
            }.setNegativeButton("取消", null).show()
        }
        container.addView(etUrl); container.addView(etTitle); container.addView(tvZoneLabel); container.addView(tvZoneValue)

        AlertDialog.Builder(activity).setTitle("加入收藏").setView(container).setPositiveButton("保存") { _, _ ->
            val finalUrl = etUrl.text.toString().trim()
            val finalTitle = etTitle.text.toString().trim()
            if (finalUrl.isEmpty()) { Toast.makeText(activity, "网址不能为空", Toast.LENGTH_SHORT).show(); return@setPositiveButton }
            activity.lifecycleScope.launch {
                val resultId = activity.db.appDao().insertWebsite(WebsiteEntity(url = finalUrl, zoneName = selectedZone, title = finalTitle))
                if (resultId != -1L) {
                    Toast.makeText(activity, "已加入收藏", Toast.LENGTH_SHORT).show()
                    activity.filterAndDisplay()
                    activity.updateBuiltinSites()
                } else {
                    Toast.makeText(activity, "已存在", Toast.LENGTH_SHORT).show()
                }
            }
        }.setNegativeButton("取消", null).show()
    }
}