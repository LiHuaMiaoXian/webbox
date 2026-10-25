package com.example.webbox

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class NotepadDialog(private val activity: MainActivity) {

    fun show() {
        val dialog = android.app.Dialog(activity)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_notepad)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))

        val themeColor = ThemeManager.getThemeColor(activity)

        val layoutWords = dialog.findViewById<LinearLayout>(R.id.layoutWords)
        val layoutNotes = dialog.findViewById<LinearLayout>(R.id.layoutNotes)
        val tabWords = dialog.findViewById<TextView>(R.id.tabWords)
        val tabNotes = dialog.findViewById<TextView>(R.id.tabNotes)

        dialog.findViewById<TextView>(R.id.btnCloseDialog)?.setOnClickListener { dialog.dismiss() }

        tabWords.setOnClickListener {
            layoutWords.visibility = View.VISIBLE; layoutNotes.visibility = View.GONE
            tabWords.setBackgroundColor(themeColor); tabWords.setTextColor(ContextCompat.getColor(activity, R.color.black))
            tabNotes.setBackgroundColor(ContextCompat.getColor(activity, R.color.pink_dark)); tabNotes.setTextColor(ContextCompat.getColor(activity, R.color.white))
        }
        tabNotes.setOnClickListener {
            layoutWords.visibility = View.GONE; layoutNotes.visibility = View.VISIBLE
            tabNotes.setBackgroundColor(themeColor); tabNotes.setTextColor(ContextCompat.getColor(activity, R.color.black))
            tabWords.setBackgroundColor(ContextCompat.getColor(activity, R.color.pink_dark)); tabWords.setTextColor(ContextCompat.getColor(activity, R.color.white))
        }

        val etSearchWord = dialog.findViewById<EditText>(R.id.etSearchWord)
        val containerWords = dialog.findViewById<LinearLayout>(R.id.containerWords)
        val btnAddWord = dialog.findViewById<TextView>(R.id.btnAddWord)

        dialog.findViewById<TextView>(R.id.btnExportWord)?.setOnClickListener { activity.createWordLauncher.launch("words_${System.currentTimeMillis()}.txt") }
        dialog.findViewById<TextView>(R.id.btnImportWord)?.setOnClickListener { activity.openWordLauncher.launch(arrayOf("text/plain")) }

        fun refreshWords(keyword: String = "") {
            containerWords.removeAllViews()
            activity.lifecycleScope.launch {
                val list = activity.db.appDao().searchNotepad(keyword)
                if (list.isEmpty()) containerWords.addView(TextView(activity).apply { text = "暂无词条"; setTextColor(Color.GRAY); setPadding(20, 20, 20, 20) })
                else list.forEach { item ->
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF2C2C2C.toInt()); setPadding(20, 20, 20, 20) }

                    val tvTitle = TextView(activity).apply {
                        text = "原词: ${item.title}"
                        setTextColor(themeColor)
                        textSize = 16f
                        setOnClickListener {
                            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", item.title))
                            Toast.makeText(activity, "已复制原词", Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(tvTitle)

                    val rawTxt = TextView(activity).apply {
                        text = "翻译: ${item.translations}"
                        setTextColor(Color.WHITE)
                        textSize = 14f
                        setPadding(0, 10, 0, 10)
                        setOnClickListener {
                            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("text", item.translations))
                            Toast.makeText(activity, "已复制翻译", Toast.LENGTH_SHORT).show()
                        }
                    }
                    row.addView(rawTxt)

                    val actionRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 10, 0, 0) }

                    val btnEdit = TextView(activity).apply {
                        text = "修改"; setTextColor(Color.CYAN); setPadding(0, 0, 30, 0)
                        setOnClickListener {
                            val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
                            val etTitle = EditText(activity).apply { setText(item.title); hint = "输入原词"; setTextColor(Color.BLACK); setHintTextColor(Color.GRAY) }
                            val etTrans = EditText(activity).apply { setText(item.translations); hint = "输入翻译"; setTextColor(Color.BLACK); setHintTextColor(Color.GRAY); minLines = 3; gravity = Gravity.TOP }
                            container.addView(etTitle)
                            container.addView(etTrans)
                            AlertDialog.Builder(activity).setTitle("修改词条").setView(container).setPositiveButton("保存") { _, _ ->
                                activity.lifecycleScope.launch {
                                    activity.db.appDao().updateNotepad(item.copy(title = etTitle.text.toString().trim(), translations = etTrans.text.toString().trim()))
                                    refreshWords(etSearchWord.text.toString())
                                }
                            }.setNegativeButton("取消", null).show()
                        }
                    }

                    val btnDelete = TextView(activity).apply {
                        text = "删除"; setTextColor(Color.RED);
                        setOnClickListener { activity.lifecycleScope.launch { activity.db.appDao().deleteNotepad(item); refreshWords(etSearchWord.text.toString()) } }
                    }

                    actionRow.addView(btnEdit)
                    actionRow.addView(btnDelete)
                    row.addView(actionRow)

                    containerWords.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 15 })
                }
            }
        }

        etSearchWord.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshWords(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnAddWord.setOnClickListener {
            val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 20, 40, 20) }
            val etTitle = EditText(activity).apply { hint = "输入原词"; setTextColor(Color.BLACK); setHintTextColor(Color.GRAY) }
            val etTrans = EditText(activity).apply { hint = "输入翻译"; setTextColor(Color.BLACK); setHintTextColor(Color.GRAY); minLines = 3; gravity = Gravity.TOP }
            container.addView(etTitle)
            container.addView(etTrans)

            AlertDialog.Builder(activity).setTitle("新建词条").setView(container).setPositiveButton("保存") { _, _ ->
                val titleStr = etTitle.text.toString().trim()
                val transStr = etTrans.text.toString().trim()
                if (titleStr.isNotEmpty() || transStr.isNotEmpty()) {
                    activity.lifecycleScope.launch {
                        activity.db.appDao().insertNotepad(NotepadEntity(title = titleStr, translations = transStr))
                        refreshWords(etSearchWord.text.toString())
                    }
                }
            }.setNegativeButton("取消", null).show()
        }
        refreshWords()

        val containerNotes = dialog.findViewById<LinearLayout>(R.id.containerNotes)
        fun refreshNotes() {
            containerNotes.removeAllViews()
            activity.lifecycleScope.launch {
                val list = activity.db.appDao().searchNotepad("记事_")
                if (list.isEmpty()) containerNotes.addView(TextView(activity).apply { text = "暂无记事"; setTextColor(Color.GRAY); setPadding(20, 20, 20, 20) })
                else list.forEach { item ->
                    val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF2C2C2C.toInt()); setPadding(20, 20, 20, 20); setOnClickListener {
                        val input = EditText(activity).apply { setText(item.translations); setPadding(40, 20, 40, 20); minLines = 5; gravity = Gravity.TOP }
                        AlertDialog.Builder(activity).setTitle(item.title).setView(input).setPositiveButton("保存") { _, _ ->
                            activity.lifecycleScope.launch { activity.db.appDao().updateNotepad(item.copy(translations = input.text.toString())); refreshNotes() }
                        }.setNegativeButton("取消", null).show()
                    } }
                    row.addView(TextView(activity).apply { text = item.title; setTextColor(themeColor); textSize = 16f })
                    row.addView(TextView(activity).apply { text = item.translations; setTextColor(Color.WHITE); textSize = 14f; setPadding(0, 10, 0, 0); maxLines = 3; ellipsize = TextUtils.TruncateAt.END })
                    row.addView(TextView(activity).apply { text = "删除"; setTextColor(Color.RED); setPadding(0, 10, 0, 0); setOnClickListener { activity.lifecycleScope.launch { activity.db.appDao().deleteNotepad(item); refreshNotes() } } })
                    containerNotes.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 15 })
                }
            }
        }

        dialog.findViewById<TextView>(R.id.btnAddNote).setOnClickListener {
            val input = EditText(activity).apply { hint = "输入记事内容"; setPadding(40, 20, 40, 20); minLines = 5; gravity = Gravity.TOP }
            AlertDialog.Builder(activity).setTitle("新建记事").setView(input).setPositiveButton("保存") { _, _ ->
                val txt = input.text.toString().trim()
                if (txt.isNotEmpty()) activity.lifecycleScope.launch { activity.db.appDao().insertNotepad(NotepadEntity(title = "记事_${System.currentTimeMillis()}", translations = txt)); refreshNotes() }
            }.setNegativeButton("取消", null).show()
        }

        dialog.findViewById<TextView>(R.id.btnExportNote).setOnClickListener {
            activity.lifecycleScope.launch {
                val list = activity.db.appDao().searchNotepad("记事_")
                if (list.isEmpty()) Toast.makeText(activity, "没有可导出的记事", Toast.LENGTH_SHORT).show()
                else {
                    val sb = StringBuilder()
                    list.forEach { sb.append("【${it.title}】\n${it.translations}\n\n") }
                    activity.noteExportContent = sb.toString(); activity.createNoteLauncher.launch("notes_${System.currentTimeMillis()}.txt")
                }
            }
        }
        dialog.findViewById<TextView>(R.id.btnImportNote).setOnClickListener { activity.openNoteLauncher.launch(arrayOf("text/plain")) }
        refreshNotes()

        dialog.show()
    }
}