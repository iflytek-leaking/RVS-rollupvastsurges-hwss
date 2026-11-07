package com.leaking.rollupvastsurges.hook

import android.os.Build

/**
 * iFLYTEK 学习机固件版本枚举。
 * 通过 try-load 特定框架类来检测。
 */
enum class RomType(val label: String) {
    UNKNOWN("Unknown"),
    A9_STANDARD("A9/A9Pro/SU30"),
    A9_HWC("A9 HWC"),
    A12_STANDARD("A12/Air/AirPro"),
    A12_HWC("A12 HWC"),
    A13_STANDARD("A13/A13Max/LearnPad"),
    A13_HWC("A13 HWC"),
    X35G("X35G/Guangyu/GuangyuPro"),
}

/** 检测当前设备固件类型 */
fun detectRomType(): RomType {
    // 先读模块 pref（允许用户手动指定）
    val forced = try {
        val ctx = Class.forName("android.app.ActivityThread")
            .getMethod("currentActivityThread").invoke(null)
        val sysCtx = ctx::class.java.getMethod("getSystemContext").invoke(ctx) as? android.content.Context
        sysCtx?.let {
            android.provider.Settings.Global.getString(it.contentResolver, "mdm_rom_type_override")
        }
    } catch (_: Throwable) { null }  // LinkageError 也是 Throwable

    if (!forced.isNullOrEmpty()) {
        return when (forced) {
            "a9" -> RomType.A9_STANDARD; "a12" -> RomType.A12_STANDARD
            "a13" -> RomType.A13_STANDARD; "x35g" -> RomType.X35G
            else -> RomType.UNKNOWN
        }
    }

    // 自动检测：通过框架类是否可加载（不抛异常）
    fun classExists(name: String): Boolean = try {
        Class.forName(name); true
    } catch (_: Throwable) { false }

    return when {
        classExists("android.iflytek.IflytekManager") -> RomType.A9_STANDARD
        classExists("android.app.mia.MiaMdmPolicyManager") -> RomType.X35G
        classExists("com.iflytek.hwc.internal.appcontrol.AppControlService") -> RomType.A13_STANDARD
        classExists("com.iflytek.hwc.service.appcontrol.AppControlService") -> {
            // A12 和 A13 都可能有此服务，用 model 辅助区分
            val model = Build.MODEL.lowercase()
            when {
                model.contains("a12") || model.contains("air") -> RomType.A12_HWC
                else -> RomType.A13_HWC
            }
        }
        else -> RomType.UNKNOWN
    }
}
