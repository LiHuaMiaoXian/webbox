package com.example.webbox

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

object SettingsManager {

    // ==========================================
    // 1. 私密系统切换与验证
    // ==========================================
    fun setupPrivateSystem(activity: MainActivity, switchBtn: TextView) {
        switchBtn.text = if (activity.isPrivateMode) "当前: 私密系统" else "当前: 公开系统"

        switchBtn.setOnClickListener {
            if (activity.privatePassword == null) {
                Toast.makeText(activity, "请先长按此按钮设置私密系统密码", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val input = EditText(activity).apply {
                hint = "请输入私密系统密码"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setPadding(40, 20, 40, 20)
            }

            AlertDialog.Builder(activity)
                .setTitle("身份验证")
                .setMessage(if (activity.isPrivateMode) "退出私密系统" else "进入私密系统")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    if (input.text.toString().trim() == activity.privatePassword) {
                        activity.performSystemSwitch()
                    } else {
                        Toast.makeText(activity, "密码错误！", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        switchBtn.setOnLongClickListener {
            val input = EditText(activity).apply {
                hint = "设置 4-8 位纯数字密码"
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                setText(activity.privatePassword ?: "")
                setPadding(40, 20, 40, 20)
            }
            AlertDialog.Builder(activity)
                .setTitle("设置私密系统密码")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val pwd = input.text.toString().trim()
                    if (pwd.length < 4) {
                        Toast.makeText(activity, "密码太短，请重新设置", Toast.LENGTH_SHORT).show()
                    } else {
                        activity.privatePassword = pwd
                        activity.prefs.edit().putString("private_system_pwd", pwd).apply()
                        Toast.makeText(activity, "私密系统密码设置成功", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
            true
        }
    }

    // ==========================================
    // 2. 广告拦截与代理
    // ==========================================
    fun showCssRulesDialog(activity: MainActivity) {
        if (activity.customCssRules.isEmpty()) {
            Toast.makeText(activity, "当前没有保存任何屏蔽规则", Toast.LENGTH_SHORT).show()
            return
        }
        val rulesArray = activity.customCssRules.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle("点击即可解除屏蔽")
            .setItems(rulesArray) { _, which ->
                val removeRule = rulesArray[which]
                activity.customCssRules.remove(removeRule)
                activity.prefs.edit().putStringSet("custom_css_rules", activity.customCssRules).apply()
                Toast.makeText(activity, "已解除对 $removeRule 的屏蔽，请刷新网页", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("清空全部规则") { _, _ ->
                activity.customCssRules.clear()
                activity.prefs.edit().putStringSet("custom_css_rules", activity.customCssRules).apply()
                Toast.makeText(activity, "已清空全部规则，请刷新网页", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    fun showProxyDialog(activity: MainActivity) {
        val input = EditText(activity).apply {
            hint = "格式如: 127.0.0.1:8080 (留空则取消代理)"
            setText(activity.prefs.getString("proxy_addr", ""))
            setPadding(40, 20, 40, 20)
        }
        AlertDialog.Builder(activity).setTitle("设置 HTTP 代理").setView(input).setPositiveButton("保存") { _, _ ->
            val proxy = input.text.toString().trim()
            activity.prefs.edit().putString("proxy_addr", proxy).apply()
            if (proxy.isEmpty()) {
                System.clearProperty("http.proxyHost"); System.clearProperty("http.proxyPort")
                Toast.makeText(activity, "代理已取消", Toast.LENGTH_SHORT).show()
            } else {
                val parts = proxy.split(":")
                if (parts.size == 2) {
                    System.setProperty("http.proxyHost", parts[0])
                    System.setProperty("http.proxyPort", parts[1])
                    Toast.makeText(activity, "代理已设置，请重启应用生效", Toast.LENGTH_LONG).show()
                } else Toast.makeText(activity, "格式错误", Toast.LENGTH_SHORT).show()
            }
        }.setNegativeButton("取消", null).show()
    }

    fun showCustomAdRulesDialog(activity: MainActivity) {
        val currentRules = activity.prefs.getString("custom_ad_rules", "") ?: ""
        val input = EditText(activity).apply { setText(currentRules); hint = "输入拦截域名"; minLines = 6; gravity = Gravity.TOP or Gravity.START; setPadding(30, 30, 30, 30) }
        AlertDialog.Builder(activity).setTitle("广告拦截规则").setView(input).setPositiveButton("保存") { _, _ ->
            activity.prefs.edit().putString("custom_ad_rules", input.text.toString().trim()).apply()
            activity.loadCustomAdRules()
            Toast.makeText(activity, "已保存", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("取消", null).show()
    }

    fun showManageWhiteListDialog(activity: MainActivity) {
        if (activity.adWhiteList.isEmpty()) { Toast.makeText(activity, "白名单为空", Toast.LENGTH_SHORT).show(); return }
        val hosts = activity.adWhiteList.toTypedArray()
        AlertDialog.Builder(activity).setTitle("点击删除白名单").setItems(hosts) { _, which ->
            val removeHost = hosts[which]
            activity.adWhiteList.remove(removeHost); activity.prefs.edit().putStringSet("ad_white_list", activity.adWhiteList).apply()
            Toast.makeText(activity, "已删除", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("关闭", null).show()
    }

    // ==========================================
    // 3. 屏蔽元素 (注入 JS)
    // ==========================================
    fun toggleBlockElementMode(activity: MainActivity) {
        val currentWeb = (if (activity.activePane == 1) activity.webView1 else activity.webView2) ?: return
        val btn = activity.findViewById<TextView>(R.id.tvBlockElementBtn)

        if (activity.isBlockingMode) {
            activity.isBlockingMode = false
            btn.text = "屏蔽元素"
            // 恢复原生默认橙色
            btn.setTextColor(ContextCompat.getColor(activity, R.color.pink_accent))
            currentWeb.evaluateJavascript("window._blockMode = false;", null)
            Toast.makeText(activity, "已取消屏蔽模式", Toast.LENGTH_SHORT).show()
        } else {
            activity.isBlockingMode = true
            btn.text = "点此取消"
            btn.setTextColor(Color.RED)
            Toast.makeText(activity, "请点击网页上你想永久屏蔽的元素（再次点击顶部可取消）", Toast.LENGTH_LONG).show()

            val blockJs = """
                (function() {
                    if (window._blockMode) return;
                    window._blockMode = true;
                    document.addEventListener('click', function(e) {
                        if (!window._blockMode) return;
                        e.preventDefault(); e.stopPropagation();
                        
                        var target = e.target;
                        if (target.tagName === 'BODY' || target.tagName === 'HTML' || target.tagName === 'MAIN') {
                            window._blockMode = false;
                            alert('不能屏蔽网页主框架！');
                            return;
                        }
                        
                        var selector = '';
                        if (target.id) selector = '#' + target.id;
                        else if (target.className && typeof target.className === 'string') {
                            var classes = target.className.trim().split(/\s+/).join('.');
                            if(classes) selector = '.' + classes;
                        } else selector = target.tagName.toLowerCase();
                        
                        target.style.setProperty('display', 'none', 'important');
                        window._blockMode = false;
                        prompt("__BLOCK_CSS__", selector);
                    }, true);
                })();
            """.trimIndent()
            currentWeb.evaluateJavascript(blockJs, null)
        }
    }

    // ==========================================
    // 4. 外观设置与翻译API设置
    // ==========================================
    fun showAppearanceDialog(activity: MainActivity) {
        val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
        val btnSelectBg = TextView(activity).apply {
            text = "从相册选择背景图"; setTextColor(Color.WHITE); setBackgroundColor(ContextCompat.getColor(activity, R.color.pink_accent)); gravity = Gravity.CENTER; setPadding(20, 20, 20, 20)
            setOnClickListener { activity.pickImageLauncher.launch(arrayOf("image/*")) }
        }
        container.addView(btnSelectBg)
        val btnClearBg = TextView(activity).apply {
            text = "恢复默认背景"; setTextColor(Color.RED); gravity = Gravity.CENTER; setPadding(20, 20, 20, 20)
            setOnClickListener { activity.prefs.edit().remove("custom_bg_uri").apply(); activity.ivBackground.visibility = android.view.View.GONE; activity.loadCustomBackground(); Toast.makeText(activity, "已恢复默认", Toast.LENGTH_SHORT).show() }
        }
        container.addView(btnClearBg)
        val tvHint = TextView(activity).apply { text = "修改预览区底色(HEX):"; setTextColor(Color.GRAY); setPadding(0, 30, 0, 10) }
        container.addView(tvHint)
        val etColor = EditText(activity).apply { hint = "输入色值如 #FF0000"; setText("#121212") }
        container.addView(etColor)
        AlertDialog.Builder(activity).setTitle("外观设置").setView(container).setPositiveButton("应用颜色") { _, _ ->
            try { val color = Color.parseColor(etColor.text.toString()); activity.previewContainer.setBackgroundColor(color); Toast.makeText(activity, "颜色已应用", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(activity, "格式错误", Toast.LENGTH_SHORT).show() }
        }.setNegativeButton("关闭", null).show()
    }

    fun showTranslateApiDialog(activity: MainActivity) {
        val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
        val etUrl = EditText(activity).apply { hint = "API URL (使用 {text} 占位)"; setText(activity.translateApiUrl) }
        val etKey = EditText(activity).apply { hint = "API Key (可选)"; setText(activity.translateApiKey) }
        container.addView(TextView(activity).apply { text = "填写你自己的翻译 API (留空使用免费接口):" }); container.addView(etUrl); container.addView(etKey)
        AlertDialog.Builder(activity).setTitle("翻译引擎设置").setView(container).setPositiveButton("保存") { _, _ ->
            activity.translateApiUrl = etUrl.text.toString().trim(); activity.translateApiKey = etKey.text.toString().trim()
            activity.prefs.edit().putString("translate_api_url", activity.translateApiUrl).putString("translate_api_key", activity.translateApiKey).apply()
            Toast.makeText(activity, "已保存", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("取消", null).show()
    }
}