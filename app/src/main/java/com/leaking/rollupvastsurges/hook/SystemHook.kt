package com.leaking.rollupvastsurges.hook

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import com.leaking.rollupvastsurges.compat.*
import com.leaking.rollupvastsurges.compat.XposedHelpers.findAndHookMethod
import com.leaking.rollupvastsurges.compat.XposedHelpers.findClass

/**
 * system_server (android) 进程的 hook
 * 包括：A9/A12/A13/X35G 框架服务 + 通用 Framework 服务 + ADB + 冻结清理
 */
class SystemHook : BaseHook() {
    override val targetPackages = setOf("android")
    override val prefKey = "hook_system"
    override fun onRegister() {
        XposedBridge.log("[MDM] Hooking SystemServer (${detectRomType().label})")

        // 加载 native penhook
        loadNativePenhook()


        // ADB force on
        runCatching {
            val ctx = HookEngine.getSystemContext() ?: return@runCatching
            if (Settings.Global.getInt(ctx.contentResolver, "adb_enabled", 0) != 1) {
                Settings.Global.putInt(ctx.contentResolver, "adb_enabled", 1)
                Settings.Global.putInt(ctx.contentResolver, "development_settings_enabled", 1)
                XposedBridge.log("[MDM] ADB enabled!")
            }
        }

        // 清理 MDM 遗留冻结
        runCatching {
            val ctx = HookEngine.getSystemContext() ?: return@runCatching
            val pm = ctx.packageManager
            val frozenRaw = Settings.Global.getString(ctx.contentResolver, "forbidden_package") ?: ""
            val pkgsToClean = frozenRaw.split(";").mapNotNull { entry ->
                val idx = entry.indexOf(':')
                if (idx > 0) entry.substring(0, idx) else null
            }.filter { it.isNotEmpty() }
            val protectPkgs = setOf(
                "com.iflytek.hwc.service", "com.iflytek.hwc",
                "com.iflytek.k12aitstatistics", "com.iflytek.ebg.aistudy.mdm",
                HookEngine.MODULE_PKG
            ) + pkgsToClean
            for (pkg in protectPkgs) {
                val state = pm.getApplicationEnabledSetting(pkg)
                if (state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                    state == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                    pm.setApplicationEnabledSetting(pkg,
                        android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0)
                    XposedBridge.log("[MDM] Re-enabled frozen pkg: $pkg")
                }
            }
        }
    }

    // ── native penhook 加载 ──
    private fun loadNativePenhook() {
        try {
            val PENHOOK_DIR = "/data/system/penhook/"
            try { java.io.File(PENHOOK_DIR).mkdirs() } catch (_: Exception) {}
            val crashFile = java.io.File("${PENHOOK_DIR}crash_count")
            val blockedFile = java.io.File("${PENHOOK_DIR}blocked")
            val statusFile = java.io.File("${PENHOOK_DIR}status")
            if (blockedFile.exists()) {
                try { statusFile.writeText("blocked") } catch (_: Exception) {}
                XposedBridge.log("[MDM] penhook: blocked, skipping"); return
            }
            var crashCount = 0
            try { crashCount = crashFile.readText().trim().toInt() } catch (_: Exception) {}
            crashCount++; try { crashFile.writeText(crashCount.toString()) } catch (_: Exception) {}
            if (crashCount >= 3) {
                try { blockedFile.createNewFile(); statusFile.writeText("blocked") } catch (_: Exception) {}
                XposedBridge.log("[MDM] penhook: crashed $crashCount times, blocked"); return
            }
            System.loadLibrary("shadowhook"); System.loadLibrary("penhook")
            try { statusFile.writeText("active") } catch (_: Exception) {}
            runCatching {
                val at = Class.forName("android.app.ActivityThread")
                val current = at.getMethod("currentActivityThread").invoke(null)
                val ctx = at.getMethod("getSystemContext").invoke(current) as Context
                Settings.Global.putString(ctx.contentResolver, "penhook_status", "active")
            }
            XposedBridge.log("[MDM] penhook: loaded")
            Thread {
                try { Thread.sleep(30000); try { crashFile.writeText("0") } catch (_: Exception) {} } catch (_: Exception) {}
            }.apply { isDaemon = true }.start()
        } catch (e: Throwable) { XposedBridge.log("[MDM] penhook: load failed: ${e.message}") }
    }
    init { HookRegistry.register(this) }
}