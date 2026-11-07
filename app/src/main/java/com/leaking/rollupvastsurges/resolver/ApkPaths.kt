package com.leaking.rollupvastsurges.resolver

/** 取目标包 APK 路径（DexKit 需要）。 */
internal object ApkPaths {
    fun sourceDir(packageName: String): String? = try {
        val at = Class.forName("android.app.ActivityThread")
        val current = at.getMethod("currentActivityThread").invoke(null)
        val ctx = at.getMethod("getSystemContext").invoke(current) as android.content.Context
        @Suppress("DEPRECATION")
        ctx.packageManager.getApplicationInfo(packageName, 0).sourceDir
    } catch (_: Throwable) {
        null
    }
}
