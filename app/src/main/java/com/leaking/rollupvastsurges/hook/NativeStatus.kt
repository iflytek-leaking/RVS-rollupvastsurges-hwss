package com.leaking.rollupvastsurges.hook

/**
 * 读取 native penhook 状态（供 UI 使用）
 *
 * 独立于 MDMModule 存在，避免加载 Xposed API 依赖的类。
 * MDMModule 实现 IXposedHookLoadPackage（compileOnly），
 * 模块 UI 进程加载它时会因缺少 Xposed bridge 类而崩溃。
 *
 * 状态来源：
 * 1. Settings.Global (system_server 写入，模块 UI 可读)
 * 2. 文件兜底（/data/system/penhook/status，仅 system_server 进程内可用）
 */
fun getNativePenhookStatus(): String {
    // 优先从 Settings.Global 读取（跨进程，模块 UI 可读）
    try {
        val at = Class.forName("android.app.ActivityThread")
        val current = at.getMethod("currentActivityThread").invoke(null)
        val ctx = at.getMethod("getSystemContext").invoke(current) as android.content.Context
        val v = android.provider.Settings.Global.getString(ctx.contentResolver, "penhook_status")
        if (v != null && v.isNotEmpty()) return v
    } catch (_: Exception) {}

    // 兜底：文件读取（仅 system_server 进程内有效）
    return try {
        val f = java.io.File("/data/system/penhook/status")
        if (f.exists()) f.readText().trim() else "unavailable"
    } catch (_: Exception) { "unknown" }
}

/**
 * native 层时间炸弹校验 — 防 Java 层被 jadx 定位 patch。
 * 返回 1=过期应停用, 0=正常, 负数=调用失败(库未加载)。
 * 校验逻辑在 libpenhook.so, 反编译 classes.dex 看不到日期明文。
 */
external fun isExpiredNative(): Int

/**
 * native 层防重打包校验 — 计算 base.apk 的 classes.dex CRC32 与期望值比对。
 * 返回 0=完整, 1=被重打包, 负数=调用失败。
 * 期望值为空字符串时不校验(debug 构建)。
 */
external fun checkIntegrityNative(expectedCrc: String): Int

/**
 * 模块完整性综合校验 (native + Java 双层):
 * native 返回 1 或 Java 校验失败 都视为过期。
 */
fun isExpiredHardened(): Boolean {
    // Java 层校验(加密字符串)
    val javaExpired = HookEngine.checkTimeBomb()
    // native 层校验(防 jadx patch Java 层)
    val nativeExpired = try { isExpiredNative() } catch (_: Throwable) { 0 }
    return javaExpired || nativeExpired == 1
}

/**
 * 防重打包综合校验:
 * Java 层 IntegrityCheck(签名校验) + native 层 checkIntegrityNative(DEX CRC)。
 */
fun isIntegrityHardened(expectedSig: String, expectedCrc: String): Boolean {
    if (!IntegrityCheck.isIntact(expectedSig)) return false
    if (expectedCrc.isEmpty()) return true
    return try { checkIntegrityNative(expectedCrc) == 0 } catch (_: Throwable) { true }
}
