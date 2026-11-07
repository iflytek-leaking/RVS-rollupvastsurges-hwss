package com.leaking.rollupvastsurges.hook

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.leaking.rollupvastsurges.compat.XposedBridge
import java.util.concurrent.ConcurrentHashMap

/**
 * 跨进程共享的工具引擎。
 * 所有状态和方法在此集中，不依赖 MDMModule 实例。
 */
object HookEngine {
    // ════════════════════════════════════════
    //  常量
    // ════════════════════════════════════════

    val MDM_PKGS = setOf("com.iflytek.ebg.aistudy.mdm", "com.iflytek.k12aitstatistics")
    val HWC_PKGS = setOf("com.iflytek.hwc.service", "com.iflytek.hwc")
    // LogService 是独立 APK（包名 com.iflytek.cbg.aistudy.logservice，非 HWC 内服务）
    val LOG_PKGS = setOf("com.iflytek.cbg.aistudy.logservice")
    val TARGET_PKGS = MDM_PKGS + HWC_PKGS + LOG_PKGS + setOf("com.leaking.rollupvastsurges", "com.toycloud.launcher", "com.iflytek.aistudy.themecenter", "com.android.launcher3")
    const val MODULE_PKG = "com.leaking.rollupvastsurges"
    // 敏感字符串加密存储（XOR+Base64）, 防反编译直接搜到提示文案
    // 注: 过期日期不再混淆硬编码, 统一由 BuildConfig.EXPIRY_DATE 注入(见下)。
    private const val EXP_M = "svXPvc7yvdPSv+3osuXdvMbFend3erL17bLbzr3p4b/m2r/Vy7La37LU7b/VzLz3+b/m1b3T0g=="
    private val EXPIRY_MSG get() = Obs.decode(EXP_M)

    // ── 过期时间 — 单一来源: BuildConfig.EXPIRY_DATE ──
    // 构建时由 app/build.gradle.kts 按 git 分支注入:
    //   含 "test" → +30 天; 含 "dev" → +14 天; main/miuix-exp/其他 → 99991231(永不过期)
    // 此处不再硬编码, 消除"构建脚本算为永不过期、却被硬编码日期杀死"的双源矛盾。
    // BuildConfig 与 HookEngine 同属模块 APK 同一类加载器, 在 Xposed 宿主进程可用
    // (与 MDMModule.kt 读 BuildConfig.RELEASE_SIG_SHA256 同一模式)。
    private val EXPIRY_DATE: String get() = try {
        val s = com.leaking.rollupvastsurges.BuildConfig.EXPIRY_DATE.toString()
        "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"   // yyyyMMdd → yyyy-MM-dd
    } catch (_: Throwable) {
        "9999-12-31"   // 读不到 BuildConfig 时放行(防御性默认, 不误杀)
    }

    val BLOCKED_KEYS = listOf(
        "forbidden_tab", "forbidden_usb", "iflytek_install_whitelist", "install_blacklist",
        "iflytek_aistudy_control_xiri", "iflytek_package_policy",
        "forbidden_dingding", "forbidden_wechat", "forbidden_qq",
        "adb_enabled", "development_settings_enabled",
        "packing_mode", "presentation_mode_enabled",
        "wifi_advanced_options_disabled", "zen_mode",
        "hwc_auto_brightness", "hwc_eye_protection",
        "com.iflytek.appshop.Installed_Non_Store_App_List"
    )
    val BLOCKED_PROPS = listOf(
        "persist.vendor.iflytek.", "persist.hwc.", "vendor.iflytek.",
        "persist.iflytek.", "persist.vendor.hwc.", "sys.iflytek."
    )

    // ── 通知栏去重 ──
    val notifiedPkgs = ConcurrentHashMap.newKeySet<String>()

    // ════════════════════════════════════════
    //  时间炸弹
    // ════════════════════════════════════════
    fun checkTimeBomb(): Boolean {
        // 控制流混淆: 插入不透明谓词伪造分支
        if (!Cfo.isEvenPriority(HookEngine.TARGET_PKGS.size)) {
            Cfo.fakeIntegrityViolation()
        }
        return try {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val expiry = fmt.parse(EXPIRY_DATE) ?: return false
            if (System.currentTimeMillis() > expiry.time) {
                secLog("[MDM] EXPIRED: $EXPIRY_MSG")
                android.widget.Toast.makeText(
                    getSystemContext(),
                    EXPIRY_MSG,
                    android.widget.Toast.LENGTH_LONG
                ).show()
                true
            } else false
        } catch (_: Exception) { false }
    }

    // ════════════════════════════════════════
    //  系统 Context
    // ════════════════════════════════════════
    private var _systemContext: Context? = null

    fun getSystemContext(): Context? {
        if (_systemContext != null) return _systemContext
        try {
            val at = Class.forName("android.app.ActivityThread")
            val current = at.getMethod("currentActivityThread").invoke(null)
            _systemContext = at.getMethod("getSystemContext").invoke(current) as? Context
        } catch (_: Exception) {}
        return _systemContext
    }

    // ════════════════════════════════════════
    //  Pref 读取 (SP 为主, Settings.Global 兜底)
    // ════════════════════════════════════════
    private var cachedModulePrefs: SharedPreferences? = null

    /** Settings.Global 兜底读取的短 TTL 缓存 —— 避免热路径(View 回调/资源加载)反复同步 IPC。 */
    private class CachedPref(val ts: Long, val value: String?)
    private val globalPrefCache = ConcurrentHashMap<String, CachedPref>()
    private const val GLOBAL_PREF_TTL_MS = 3000L

    private fun readGlobalPref(key: String): String? {
        val now = System.currentTimeMillis()
        globalPrefCache[key]?.let { if (now - it.ts < GLOBAL_PREF_TTL_MS) return it.value }
        val v = try {
            val ctx = getSystemContext()
            if (ctx == null) null
            else Settings.Global.getString(ctx.contentResolver, "rollupvastsurges_pref_" + key)
        } catch (_: Exception) { null }
        globalPrefCache[key] = CachedPref(now, v)
        return v
    }

    private fun readModulePrefs(): SharedPreferences? {
        if (cachedModulePrefs != null) return cachedModulePrefs
        try {
            val ctx = getSystemContext() ?: return null
            cachedModulePrefs = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
        } catch (_: Exception) {}
        return cachedModulePrefs
    }

    fun readStr(key: String, default: String = ""): String {
        // 仅当 SP 里真有该 key 才用 SP 值；否则走 Settings.Global（跨进程真实配置源）。
        // 注意: getString(key, default) 对缺失 key 返回 default（非 null），不能用来判断存在性。
        try {
            val sp = readModulePrefs()
            if (sp != null && sp.contains(key)) return sp.getString(key, default) ?: default
        } catch (_: Exception) {}
        readGlobalPref(key)?.let { return it }
        return default
    }

    fun readBool(key: String, default: Boolean = true): Boolean {
        try {
            val sp = readModulePrefs()
            if (sp != null && sp.contains(key)) return sp.getBoolean(key, default)
        } catch (_: Exception) {}
        try {
            val sp = readModulePrefs()
            if (sp != null && sp.contains(key)) return sp.getString(key, null)
                ?.let { it == "1" || it.equals("true", ignoreCase = true) } ?: default
        } catch (_: Exception) {}
        // 目标进程无模块 prefs → 回退 Settings.Global（跨进程）
        // 注意: UI 写入 "1"/"0"，不能直接 toBoolean()（"1".toBoolean()==false）
        readGlobalPref(key)?.let { return it == "1" || it.equals("true", ignoreCase = true) }
        return default
    }

    fun readInt(key: String, default: Int = 0): Int {
        try {
            val sp = readModulePrefs()
            if (sp != null && sp.contains(key)) return sp.getInt(key, default)
        } catch (_: Exception) {}
        try {
            val sp = readModulePrefs()
            if (sp != null && sp.contains(key)) return sp.getString(key, null)?.toIntOrNull() ?: default
        } catch (_: Exception) {}
        readGlobalPref(key)?.toIntOrNull()?.let { return it }
        return default
    }

    // ════════════════════════════════════════
    //  Hook 状态记录
    // ════════════════════════════════════════
    private val _hookStatus = mutableMapOf<String, Boolean>()

    fun markActive(key: String) {
        _hookStatus[key] = true
        writeHookStatus(key, true)
    }

    fun markFailed(key: String) {
        _hookStatus[key] = false
        writeHookStatus(key, false)
    }

    fun readHookStatus(key: String): Boolean? = _hookStatus[key]

    private fun writeHookStatus(key: String, active: Boolean) {
        try {
            // key 统一带 hook_status_ 前缀，与读取方（SettingsActivity/RollupAPI）一致
            readModulePrefs()?.edit()?.putBoolean("hook_status_$key", active)?.apply()
            val ctx = getSystemContext() ?: return
            Settings.Global.putString(ctx.contentResolver,
                "hook_status_$key", if (active) "1" else "0")
        } catch (_: Exception) {}
    }

    fun readHookStatusFromFile(key: String): Boolean? {
        try {
            val ctx = getSystemContext() ?: return null
            val v = Settings.Global.getString(ctx.contentResolver, "hook_status_$key")
            return when (v) {
                "1" -> true; "0" -> false; else -> null
            }
        } catch (_: Exception) { return null }
    }

    // ════════════════════════════════════════
    //  崩溃计数 (跨进程, 文件持久化)
    // ════════════════════════════════════════
    private const val CRASH_THRESHOLD = 3
    private const val CRASH_COOLDOWN_SEC = 60  // 60 秒后自动重置

    private fun getCrashPrefs(): SharedPreferences? {
        try {
            val ctx = getSystemContext() ?: return null
            return ctx.getSharedPreferences("mdm_crash_record", 0)
        } catch (_: Exception) { return null }
    }

    fun crashCount(pkg: String): Int {
        try { return getCrashPrefs()?.getInt(pkg.replace('.', '_'), 0) ?: 0 } catch (_: Exception) { return 0 }
    }

    fun crashTimestamp(pkg: String): Long {
        try { return getCrashPrefs()?.getLong("${pkg.replace('.', '_')}_ts", 0L) ?: 0L } catch (_: Exception) { return 0L }
    }

    fun incrementCrash(pkg: String) {
        try {
            val prefs = getCrashPrefs() ?: return
            val key = pkg.replace('.', '_')
            val count = prefs.getInt(key, 0) + 1
            prefs.edit().putInt(key, count).putLong("${key}_ts", System.currentTimeMillis()).apply()
        } catch (_: Exception) {}
    }

    fun resetCrash(pkg: String) {
        try {
            val key = pkg.replace('.', '_')
            getCrashPrefs()?.edit()?.putInt(key, 0)?.putLong("${key}_ts", 0L)?.apply()
        } catch (_: Exception) {}
    }

    fun shouldSkip(pkg: String): Boolean {
        if (pkg !in TARGET_PKGS && pkg != MODULE_PKG) return false
        val count = crashCount(pkg)
        if (count < CRASH_THRESHOLD) return false
        val ts = crashTimestamp(pkg)
        if (ts > 0 && (System.currentTimeMillis() - ts) / 1000L > CRASH_COOLDOWN_SEC) {
            resetCrash(pkg)
            return false
        }
        return true
    }

    // ════════════════════════════════════════
    //  通知栏注入
    // ════════════════════════════════════════
    private const val NOTIFY_CHANNEL = "mdm_disabler_inject"

    fun showInjectionNotify(activity: Activity, pkg: String) {
        try {
            val nm = activity.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(
                    NotificationChannel(NOTIFY_CHANNEL, "MDMDisabler", NotificationManager.IMPORTANCE_LOW))
            }
            val label = try {
                activity.packageManager.getApplicationLabel(
                    activity.packageManager.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) { pkg }
            val notify = Notification.Builder(activity, NOTIFY_CHANNEL)
                .setContentTitle("MDMDisabler")
                .setContentText("已注入 $label")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setAutoCancel(true)
                .build()
            nm.notify(pkg.hashCode(), notify)
        } catch (_: Exception) {}
    }

    // ════════════════════════════════════════
    //  删除旧缓存
    // ════════════════════════════════════════
    fun clearCache() {
        cachedModulePrefs = null
        _systemContext = null
    }
}
