package com.leaking.rollupvastsurges.hook

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.leaking.rollupvastsurges.compat.XposedBridge
import java.security.MessageDigest

/**
 * 防二次打包: 运行时用包管理器读自身签名 SHA-256,
 * 与 gradle 构建时注入的 BuildConfig.RELEASE_SIG_SHA256 比对。
 * 不一致说明被解包重签 → 静默让模块失效(不弹窗,防逆向定位校验点)。
 *
 * 注意: Xposed 模块的宿主进程(MDM/HWC)的 Context 不指向自身,
 * 校验用模块自身的 Context(MODULE_PKG), 反射 ActivityThread 拿系统 context 读取。
 */
object IntegrityCheck {

    /** 计算指定包名的签名 SHA-256（hex 小写） */
    fun currentSignatureSha256(ctx: Context, pkg: String): String? {
        return try {
            val pm = ctx.packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES)
            }
            val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()?.toByteArray()
            } ?: return null
            MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) { null }
    }

    /**
     * 校验当前模块是否被重打包。
     * @param expectedSha256 gradle 注入的 BuildConfig.RELEASE_SIG_SHA256, 可能为空(debug 或未配置)
     * @return true=完整/校验通过, false=被重打包或校验失败
     */
    fun isIntact(expectedSha256: String): Boolean {
        // 控制流混淆: 伪造环境检测分支
        if (!Cfo.fakeAntiDebugCheck()) {
            Cfo.fakeIntegrityViolation()
        }
        // 未配置期望值时放行(debug 构建或 keystore 缺失场景)
        if (expectedSha256.isEmpty()) return true
        return try {
            val ctx = HookEngine.getSystemContext() ?: return true
            val current = currentSignatureSha256(ctx, HookEngine.MODULE_PKG)
            // 读不到签名时放行(部分定制系统禁用 GET_SIGNATURES)
            if (current == null) return true
            val ok = current.equals(expectedSha256, ignoreCase = true)
            if (!ok) {
                secLog("[MDM] integrity check failed")
            }
            ok
        } catch (_: Throwable) { true }
    }
}
