package com.example.webbox

// 标签页数据模型
data class TabData(
    val id: Int,
    var title: String,
    var url: String,
    var isActive: Boolean = false
)

object TabManager {
    // 内存中维护的标签页列表
    val openTabs = mutableListOf<TabData>()

    // 自增 ID 生成器
    private var nextId = 1

    // 标签页数量上限
    private const val MAX_TABS = 5

    /**
     * 新增标签页
     * @return 如果超出容量上限，返回需要被销毁的最老标签的 ID（供外部销毁 WebView），否则返回 null
     */
    fun addTab(title: String, url: String): Int? {
        var idToDestroy: Int? = null

        // 如果达到上限，移除最老的一个（列表的第一个）
        if (openTabs.size >= MAX_TABS) {
            val oldestTab = openTabs.removeAt(0)
            idToDestroy = oldestTab.id
        }

        // 新增之前，把所有现有的标签置为非激活状态
        openTabs.forEach { it.isActive = false }

        // 创建并添加新标签，默认激活
        val newTab = TabData(
            id = nextId++,
            title = title,
            url = url,
            isActive = true
        )
        openTabs.add(newTab)

        return idToDestroy
    }

    /**
     * 移除指定 ID 的标签页
     */
    fun removeTab(id: Int) {
        val index = openTabs.indexOfFirst { it.id == id }
        if (index != -1) {
            val wasActive = openTabs[index].isActive
            openTabs.removeAt(index)

            // 如果被移除的是当前激活的标签，尝试激活列表中的最后一个（或者第一个）
            if (wasActive && openTabs.isNotEmpty()) {
                openTabs.last().isActive = true
            }
        }
    }

    /**
     * 获取当前标签页数量
     */
    fun getTabCount(): Int {
        return openTabs.size
    }

    /**
     * 更新当前激活的标签页信息（在 WebView 页面加载完成时调用）
     */
    fun updateCurrentTabInfo(title: String, url: String) {
        openTabs.find { it.isActive }?.let { currentTab ->
            currentTab.title = title
            currentTab.url = url
        }
    }
}