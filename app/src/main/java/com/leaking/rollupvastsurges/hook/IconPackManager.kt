package com.leaking.rollupvastsurges.hook

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import kotlin.text.Charsets

/**
 * 卷巨澜图标包管理器 — 扫描/切换/缓存标准 Android 图标包。
 *
 * 图标包规范：https://developer.android.com/develop/ui/views/launch/icon-packs
 *
 * 工作方式：
 * 1. 扫描 Intent("com.android.launcher.action.INSTALL_ICON_PACK") 注册的应用
 * 2. 对每个 App 包名，去图标包里找 drawable/com_example_app 资源
 * 3. 找到即替换桌面图标
 */
object IconPackManager {

    private const val TAG = "IconPackManager"
    private const val ICON_PACK_INTENT = "com.android.launcher.action.INSTALL_ICON_PACK"
    // 备选：部分图标包用不同 action
    private const val ICON_PACK_INTENT_ALT = "org.adw.launcher.THEMES"

    /** 已安装的图标包信息 */
    data class IconPackInfo(
        val packageName: String,
        val label: String,
        val iconCount: Int
    )

    // LRU 缓存 (资源保活+防泄漏)
    private val resourceCache = object : LinkedHashMap<String, Resources>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Resources>?): Boolean {
            return size > 4  // 最多缓存 4 个图标包的 Resources
        }
    }

    /**
     * 扫描已安装的图标包。
     * 可在任何进程调用（PackageManager 跨进程可用）。
     */
    fun scanIconPacks(pm: PackageManager): List<IconPackInfo> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<IconPackInfo>()
        for (intentAction in listOf(ICON_PACK_INTENT, ICON_PACK_INTENT_ALT)) {
            try {
                val intent = android.content.Intent(intentAction)
                for (ri in pm.queryIntentActivities(intent, 0)) {
                    val pkg = ri.activityInfo.packageName
                    if (!seen.add(pkg)) continue
                    try {
                        val res = pm.getResourcesForApplication(pkg)
                        result.add(IconPackInfo(pkg, ri.loadLabel(pm).toString(), countIcons(res, pkg)))
                    } catch (_: Exception) {}
                }
            } catch (_: Exception) {}
        }
        for (pkg in listOf("com.andevs.iconpack", "com.novalauncher.theme", "com.vertumus.flyme", "com.stealthychief.iconpack",
            "me.morirain.dev.iconpack.pure")) {
            if (!seen.add(pkg)) continue
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                result.add(IconPackInfo(pkg, ai.loadLabel(pm).toString(), countIcons(pm.getResourcesForApplication(pkg), pkg)))
            } catch (_: Exception) {}
        }
        return result.sortedByDescending { it.iconCount }
    }

    /**
     * 获取图标包的 Resources（带 LRU 缓存）。
     */
    fun getIconPackResources(pm: PackageManager, pkg: String): Resources? {
        // 先从缓存取
        resourceCache[pkg]?.let { return it }
        return try {
            val res = pm.getResourcesForApplication(pkg)
            resourceCache[pkg] = res
            res
        } catch (_: Exception) { null }
    }

    /**
     * 清除资源缓存（图标包更新/卸载时调用）。
     */
    fun clearCache() {
        resourceCache.clear()
        appFilterCache.clear()
    }

    /**
     * 根据 App 包名从图标包中获取 Drawable。
     * 兼容格式：
     *   com.example.app → com_example_app (drawable)
     *   com.example.app → com_example_app.xml (vector, drawable)
     */
    fun getIconDrawable(res: Resources, iconPackPkg: String, appPackage: String): Drawable? {
        val resName = appPackage.replace('.', '_')

        // 尝试原始名称
        var id = res.getIdentifier(resName, "drawable", iconPackPkg)
        if (id != 0) return getDrawableCompat(res, id)

        // 尝试小写 (有些图标包强制小写)
        id = res.getIdentifier(resName.lowercase(), "drawable", iconPackPkg)
        if (id != 0) return getDrawableCompat(res, id)

        // 尝试 mipmap (有些图标包放 mipmap)
        id = res.getIdentifier(resName, "mipmap", iconPackPkg)
        if (id != 0) return getDrawableCompat(res, id)

        // 回退: 解析 assets/appfilter.xml (Nova/ADW/Lawnchair 等)
        val afName = resolveAppFilterName(res, iconPackPkg, appPackage)
        if (afName != null) {
            id = res.getIdentifier(afName, "drawable", iconPackPkg)
            if (id != 0) return getDrawableCompat(res, id)
            id = res.getIdentifier(afName, "mipmap", iconPackPkg)
            if (id != 0) return getDrawableCompat(res, id)
        }

        return null
    }

    // ── appfilter.xml 解析（Nova/ADW/Apex 等现代图标包标准）──
    // 格式: <item component="ComponentInfo{com.app.package/Activity}" drawable="icon_name" />
    private val appFilterCache = mutableMapOf<String, Map<String, String>>()

    private fun resolveAppFilterName(res: Resources, iconPackPkg: String, appPackage: String): String? {
        appFilterCache[iconPackPkg]?.get(appPackage)?.let { return it }
        try {
            val xmlText = res.assets.open("appfilter.xml")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val map = mutableMapOf<String, String>()
            // 匹配: ComponentInfo{package/activity} drawable="name"
            // 兼容双引号和单引号两种 XML 属性写法
            val regex = Regex("""ComponentInfo\{([^/]+)/[^}]+\}["']\s+drawable=["']([^"']+)["']""")
            for (m in regex.findAll(xmlText)) {
                // 保留每个包的第一个匹配（activity 优先级最高的）
                val pkg = m.groupValues[1]
                val drawable = m.groupValues[2]
                if (pkg !in map) map[pkg] = drawable
            }
            appFilterCache[iconPackPkg] = map
            return map[appPackage]
        } catch (_: Exception) { return null }
    }

    // ── 私有方法 ──

    private fun countIcons(res: Resources, pkg: String): Int {
        // 优先从 appfilter.xml 统计（现代图标包标准）
        try {
            val text = res.assets.open("appfilter.xml")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val regex = Regex("""drawable=["']([^"']+)["']""")
            val names = regex.findAll(text).map { it.groupValues[1] }.distinct().toList()
            if (names.size > 10) return names.size
        } catch (_: Exception) {}
        // 回退：抽样统计（旧式图标包）
        return try {
            val testPkgs = listOf("com.android.chrome", "com.android.vending",
                "com.android.settings", "com.tencent.mm", "com.iflytek")
            testPkgs.count { test ->
                val name = test.replace('.', '_')
                res.getIdentifier(name, "drawable", pkg) != 0 ||
                res.getIdentifier(name.lowercase(), "drawable", pkg) != 0
            } * 20  // 估算：5 个测试包 * 20 ≈ 总图标数
        } catch (_: Exception) { 0 }
    }

    private fun getDrawableCompat(res: Resources, id: Int): Drawable? {
        return try {
            // API 兼容：最高可用到 getDrawable(int, Theme)
            @Suppress("DEPRECATION")
            res.getDrawable(id)
        } catch (_: Exception) { null }
    }
}
