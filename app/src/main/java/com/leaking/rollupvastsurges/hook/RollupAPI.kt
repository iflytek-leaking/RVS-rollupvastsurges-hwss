package com.leaking.rollupvastsurges.hook

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * 卷巨澜卡牌 API — 自定义卡牌可安全调用的公开接口。
 *
 * 本对象是纯 Kotlin，不依赖 Xposed API，可在任何进程使用。
 * 所有方法接受一个 Context 参数（卡牌中可直接传 mActivity）。
 *
 * 用法示例（在自定义 BaseLauncherHolder 中）：
 * ```
 * class MyCard(activity: Activity, meta: BizCardMeta) : BaseLauncherHolder(activity, meta) {
 *     override fun initView(context: Context, rootView: ViewGroup) {
 *         val enabled = RollupAPI.isProtectionActive(context, "func_app_control")
 *         val status = RollupAPI.getHookStatus(context, "hook_tyelauncher_wallpaper")
 *         val hz = RollupAPI.getCurrentRefreshRate(context)
 *     }
 * }
 * ```
 */
object RollupAPI {

    private const val PREFS_NAME = "rollupvastsurges_prefs"
    private const val TAG = "RollupAPI"

    // ═══════════════════════════════════════════════════════════════
    // 一、Pref 读写（与 MDMModule / SettingsActivity 共用同一 SP）
    // ═══════════════════════════════════════════════════════════════

    /** 读取模块布尔型配置 */
    fun readPref(context: Context, key: String, default: Boolean = false): Boolean {
        return try {
            context.getSharedPreferences(PREFS_NAME, 0).getBoolean(key, default)
        } catch (_: Exception) { default }
    }

    /** 写入模块布尔型配置（卡牌开关 → 立即生效） */
    fun writePref(context: Context, key: String, value: Boolean) {
        try {
            context.getSharedPreferences(PREFS_NAME, 0)
                .edit().putBoolean(key, value).apply()
        } catch (_: Exception) {}
    }

    /** 读取模块字符串型配置 */
    fun readPrefString(context: Context, key: String, default: String = ""): String {
        return try {
            context.getSharedPreferences(PREFS_NAME, 0).getString(key, default) ?: default
        } catch (_: Exception) { default }
    }

    /** 写入模块字符串型配置 */
    fun writePrefString(context: Context, key: String, value: String) {
        try {
            context.getSharedPreferences(PREFS_NAME, 0)
                .edit().putString(key, value).apply()
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════════════════════════════
    // 二、防护功能快捷查询
    // ═══════════════════════════════════════════════════════════════

    /** 某项防护是否启用（返回 true = 正在拦截） */
    fun isProtectionActive(context: Context, prefKey: String): Boolean {
        return readPref(context, prefKey, true)
    }

    /** 列出所有已启用的防护名称 */
    fun listActiveProtections(context: Context): List<String> {
        val map = mapOf(
            "func_app_control" to "应用冻结",
            "func_app_install" to "安装拦截",
            "func_network_control" to "联网管控",
            "func_usb" to "USB禁用",
            "func_adb_keep" to "ADB保持",
            "func_stats_report" to "使用时长",
        )
        return map.filter { (key, _) -> readPref(context, key, true) }.values.toList()
    }

    /** 一键切换某项防护（返回切换后的状态） */
    fun toggleProtection(context: Context, prefKey: String): Boolean {
        val current = readPref(context, prefKey, true)
        val new = !current
        writePref(context, prefKey, new)
        return new
    }

    // ═══════════════════════════════════════════════════════════════
    // 三、Hook 状态查询（跨进程读取 Settings.Global）
    // ═══════════════════════════════════════════════════════════════

    /** 查询某个 hook 是否已成功注入（通过 Settings.Global 跨进程共享） */
    fun getHookStatus(context: Context, hookKey: String): Boolean? {
        return try {
            val v = Settings.Global.getString(context.contentResolver, "hook_status_${hookKey}")
            when (v) { "1" -> true; "0" -> false; else -> null }
        } catch (_: Exception) { null }
    }

    /** 查询 native penhook 是否已启用 */
    fun getNativePenhookStatus(context: Context): String {
        try {
            val v = Settings.Global.getString(context.contentResolver, "penhook_status")
            if (v != null && v.isNotEmpty()) return v
        } catch (_: Exception) {}
        return try {
            java.io.File("/data/system/penhook/status").readText().trim()
        } catch (_: Exception) { "unknown" }
    }

    /** 查询 penhook 擦除模式当前是否激活 */
    fun getEraserModeActive(): Boolean {
        return try {
            java.io.File("/data/system/penhook/eraser_mode_active").readText().trim() == "1"
        } catch (_: Exception) { false }
    }

    // ═══════════════════════════════════════════════════════════════
    // 四、系统实用工具
    // ═══════════════════════════════════════════════════════════════

    /** 获取当前屏幕刷新率 (Hz) */
    fun getCurrentRefreshRate(context: Context): Int {
        return try {
            @Suppress("DEPRECATION")
            val display = (context.getSystemService(Context.WINDOW_SERVICE)
                as? android.view.WindowManager)?.defaultDisplay
            val mode = display?.mode
            mode?.refreshRate?.toInt() ?: 60
        } catch (_: Exception) {
            try {
                val sp = Class.forName("android.os.SystemProperties")
                val get = sp.getMethod("get", String::class.java, String::class.java)
                (get.invoke(null, "persist.vendor.hwc.refresh_rate", "60") as String).toIntOrNull() ?: 60
            } catch (_: Exception) { 60 }
        }
    }

    /** 设置屏幕刷新率（多条属性尝试） */
    fun setRefreshRate(hz: Int) {
        try {
            val sp = Class.forName("android.os.SystemProperties")
            val set = sp.getMethod("set", String::class.java, String::class.java)
            for (prop in listOf(
                "persist.vendor.hwc.refresh_rate",
                "persist.vendor.display.refresh_rate",
                "debug.egl.swapinterval"
            )) {
                try { set.invoke(null, prop, hz.toString()) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════════════════════════════
    // 五、设备信息
    // ═══════════════════════════════════════════════════════════════

    /** 检测 ROM 类型 */
    fun detectRomType(): String {
        return try {
            val sp = Class.forName("android.os.SystemProperties")
            val get = sp.getMethod("get", String::class.java, String::class.java)
            val mfr = get.invoke(null, "ro.product.system.manufacturer", "") as String
            when {
                mfr.equals("iFLYTEK", ignoreCase = true) ->
                    when { Build.VERSION.SDK_INT >= 33 -> "A13"; Build.VERSION.SDK_INT >= 31 -> "A12"; else -> "A9" }
                (get.invoke(null, "ro.build.version.external", "") as? String)
                    ?.contains("Lenovo", ignoreCase = true) == true -> "X35G"
                else -> "unknown"
            }
        } catch (_: Exception) { "unknown" }
    }

    /** 当前是否运行在 system UID */
    fun isSystemUid(): Boolean {
        return android.os.Process.myUid() == android.os.Process.SYSTEM_UID
    }

    /** 获取设备型号信息 */
    fun getDeviceModel(): String = Build.MODEL

    /** 获取 Android API 级别 */
    fun getApiLevel(): Int = Build.VERSION.SDK_INT

    // ═══════════════════════════════════════════════════════════════
    // 六、TyeLauncher 专用
    // ═══════════════════════════════════════════════════════════════

    /**
     * 检查当前是否已启用壁纸跟随系统。
     * 等效于 SettingsActivity 中的"壁纸跟随系统"开关。
     */
    fun isWallpaperFollowEnabled(context: Context): Boolean {
        return readPref(context, "tyelauncher_follow_system_wallpaper", false)
    }

    /**
     * 检查当前是否已启用自定义卡牌注入。
     * 等效于 SettingsActivity 中的"注入自定义卡牌"开关。
     */
    fun isCustomCardsEnabled(context: Context): Boolean {
        return readPref(context, "tyelauncher_custom_cards", false)
    }

    /**
     * 获取已启用的 TyeLauncher 定制功能列表。
     */
    fun getTyeLauncherFeatures(context: Context): List<String> {
        val features = mutableListOf<String>()
        if (readPref(context, "tyelauncher_follow_system_wallpaper", false)) features.add("壁纸跟随系统")
        if (readPref(context, "tyelauncher_workspace_mode", false)) features.add("桌面模式(Workspace)")
        if (readPref(context, "tyelauncher_custom_cards", false)) features.add("卡牌注入")
        if (readPref(context, "tyelauncher_custom_bg", false)) features.add("自定义背景")
        return features
    }
}
