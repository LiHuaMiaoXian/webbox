package com.example.webbox

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.TextView

object ThemeManager {
    // 彻底砍掉换色功能，永远只返回默认的橙色
    fun getThemeColor(context: Context): Int {
        return Color.parseColor("#ff6d00")
    }

    fun applyTheme(activity: MainActivity) {
        val themeColor = Color.parseColor("#ff6d00")
        val defaultOrange = Color.parseColor("#ff6d00")

        try {
            val root = activity.window.decorView.findViewById<android.view.View>(android.R.id.content)
            // 强制把所有因为之前折腾残留的蓝色/绿色，全部刷回橙色
            updateViewColors(root, themeColor)

            // 强制重绘特殊背景为橙色
            activity.findViewById<android.view.View>(R.id.randomBtn)?.let { view ->
                val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(themeColor); setStroke(2, themeColor) }
                view.background = bg
            }
            activity.findViewById<android.view.View>(R.id.sidebarToggle)?.let { view ->
                val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor("#424242")); setStroke(2, themeColor) }
                view.background = bg
            }
            activity.findViewById<android.view.View>(R.id.fabContainer)?.let { view ->
                val bg = GradientDrawable().apply { cornerRadius = 40f; setColor(Color.parseColor("#424242")); setStroke(2, themeColor) }
                view.background = bg
            }

            activity.adapter.notifyDataSetChanged()
            activity.renderZoneTabs()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateViewColors(view: android.view.View, newColor: Int) {
        try {
            if (view is TextView) {
                val c = view.currentTextColor
                // 强行把所有不是白色、不是黑色的文本，全部染成橙色
                if (c != Color.WHITE && c != Color.BLACK && c != Color.GRAY && c != Color.RED && c != Color.CYAN) {
                    view.setTextColor(newColor)
                }
            }

            if (view is android.widget.CheckBox) {
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked))
                view.buttonTintList = ColorStateList(states, intArrayOf(newColor, Color.GRAY))
            }

            val bg = view.background
            if (bg is GradientDrawable) {
                val colorStateList = bg.color
                if (colorStateList != null) {
                    val bgColor = colorStateList.defaultColor
                    // 用 Int 常量替换 Color.parseColor，消除黄线警告
                    val darkGray = 0xFF424242.toInt()
                    val transparentBlack1 = 0x99000000.toInt()
                    val transparentBlack2 = 0x33000000.toInt()

                    if (bgColor != darkGray && bgColor != Color.BLACK && bgColor != transparentBlack1 && bgColor != transparentBlack2) {
                        val mutatedBg = bg.mutate() as GradientDrawable
                        mutatedBg.color = ColorStateList.valueOf(newColor)
                    }
                }
            }

            if (view is android.view.ViewGroup) {
                for (i in 0 until view.childCount) {
                    updateViewColors(view.getChildAt(i), newColor)
                }
            }
        } catch (e: Exception) {}
    }
}