package com.leaking.rollupvastsurges.hook

import android.content.Context
import java.io.File

/**
 * 卷巨澜主题管理器 — 扫描/安装/切换 TyeLauncher 主题皮肤。
 *
 * 主题系统由 ThemeCenter (com.iflytek.aistudy.themecenter) 管理，
 * 模块接管 UI 后可实现第三方主题加载。
 *
 * 文档参考：docs/32-TyeLauncher-主题皮肤加载机制.md
 */
object SkinUtils {

    const val THEME_CENTER = "com.iflytek.aistudy.themecenter"
    const val TYE_LAUNCHER = "com.toycloud.launcher"
    // Settings.Global 中主题持久化的真实 key
    const val SETTINGS_THEME_KEY = "com.iflytek.aistudy.themecenter.theme_system_current_theme"

    /** 已安装的皮肤信息 */
    data class SkinInfo(
        val id: String,
        val displayName: String,
        val path: String,
        val size: Long,
        val isThirdParty: Boolean = false  // true = 用户手动导入的第三方主题
    )

    // ── 扫描 ──

    /**
     * 扫描已安装的皮肤。
     *
     * @param includeExternal 是否扫描外部存储中的第三方 .skin
     */
    fun scanInstalledSkins(context: Context, includeExternal: Boolean = true): List<SkinInfo> {
        val result = mutableListOf<SkinInfo>()

        // 1) ThemeCenter 目录内的主题
        try {
            val tcCtx = context.createPackageContext(THEME_CENTER, 0)
            val themesDir = File(tcCtx.filesDir, "themes")
            if (themesDir.exists()) {
                for (dir in themesDir.listFiles().orEmpty()) {
                    if (!dir.isDirectory) continue
                    val themeId = dir.name
                    val skinFile = File(dir, "$TYE_LAUNCHER.skin")
                    if (skinFile.isFile) {
                        result.add(SkinInfo(
                            id = themeId,
                            displayName = tryReadAppName(skinFile) ?: themeId,
                            path = skinFile.absolutePath,
                            size = skinFile.length()
                        ))
                    }
                }
            }
        } catch (_: Exception) {}

        // 2) 外部存储中的第三方主题
        if (includeExternal) {
            for (base in listOf(
                "/sdcard/themes",
                "/sdcard/Android/data/$TYE_LAUNCHER/files/themes",
                "/storage/emulated/0/themes"
            )) {
                val dir = File(base)
                if (!dir.exists()) continue
                for (f in dir.listFiles().orEmpty()) {
                    if (f.isDirectory) continue
                    // .skin → 直接导入后放入的，.zip → 官方主题包
                    if (f.extension != "skin" && f.extension != "zip") continue
                    val themeId = f.nameWithoutExtension
                    // 去重
                    if (result.any { it.id == themeId }) continue
                    result.add(SkinInfo(
                        id = "third_$themeId",
                        displayName = f.nameWithoutExtension,
                        path = f.absolutePath,
                        size = f.length(),
                        isThirdParty = true
                    ))
                }
            }
        }

        return result.sortedBy { it.displayName }
    }

    // ── 安装 ──

    /**
     * 安装单个 .skin 文件到 ThemeCenter 目录。
     */
    fun installSkin(context: Context, sourceFile: File, themeId: String? = null): String? {
        return try {
            val tcCtx = context.createPackageContext(THEME_CENTER, 0)
            val id = themeId ?: sourceFile.nameWithoutExtension
            val targetDir = File(tcCtx.filesDir, "themes/$id")
            targetDir.mkdirs()
            val targetFile = File(targetDir, "$TYE_LAUNCHER.skin")
            sourceFile.copyTo(targetFile, overwrite = true)
            id
        } catch (_: Exception) { null }
    }

    /**
     * 安装官方主题 ZIP 包。
     *
     * 官方主题发布格式（docs/32 §六）：
     *   <themeId>.zip
     *   ├── theme_config.json    ← 主题清单
     *   ├── com.toycloud.launcher.skin  ← TyeLauncher 皮肤
     *   ├── com....iflypenota.skin      ← 学习笔皮肤（可选）
     *   └── lock_screen.zip             ← 锁屏壁纸（可选）
     *
     * @return 安装后的 themeId + displayName（失败返回 null）
     */
    data class ThemeInstallResult(val themeId: String, val displayName: String)

    fun installThemeZip(context: Context, zipFile: File): ThemeInstallResult? {
        return try {
            val zis = java.util.zip.ZipInputStream(java.io.FileInputStream(zipFile))
            var themeId: String? = null
            var displayName: String? = null
            val skinEntries = mutableListOf<Pair<String, ByteArray>>()  // fileName → data

            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val data = zis.readBytes()

                when {
                    // 读取 theme_config.json
                    name == "theme_config.json" -> {
                        try {
                            val gson = com.google.gson.Gson()
                            val map = gson.fromJson(data.decodeToString(), Map::class.java)
                            themeId = map?.get("themeId") as? String
                            displayName = map?.get("name") as? String
                        } catch (_: Exception) {}
                    }
                    // 收集 .skin 文件
                    name.endsWith(".skin") -> skinEntries.add(name to data)
                }
                entry = zis.nextEntry
            }
            zis.close()

            val id = themeId ?: return null
            val name = displayName ?: id
            if (skinEntries.isEmpty()) return null

            // 安装到 ThemeCenter 目录
            val tcCtx = context.createPackageContext(THEME_CENTER, 0)
            val targetDir = File(tcCtx.filesDir, "themes/$id")
            targetDir.mkdirs()

            for ((fileName, data) in skinEntries) {
                File(targetDir, fileName).writeBytes(data)
            }

            ThemeInstallResult(id, name)
        } catch (_: Exception) { null }
    }

    // ── 切换 ──

    /**
     * 切换当前主题（写 Settings.Global → 触发 ThemeCenter 的 GlobalSettingsObserver）。
     */
    fun switchSkin(context: Context, themeId: String): Boolean {
        return try {
            val gson = com.google.gson.Gson()
            val themeInfo = mapOf(
                "theme_id" to themeId,
                "theme_type" to "static_theme",
                "theme_mode" to "light",
                "is_system_default_theme" to (themeId == "default"),
                "protocol_version" to "2"
            )
            android.provider.Settings.Global.putString(
                context.contentResolver,
                SETTINGS_THEME_KEY,
                gson.toJson(themeInfo)
            )
            // 记录到模块 SP
            context.getSharedPreferences("rollupvastsurges_prefs", 0)
                .edit().putString("active_skin_id", themeId).apply()
            true
        } catch (_: Exception) { false }
    }

    // ── 获取当前主题 ──

    fun getCurrentSkinId(context: Context, classLoader: ClassLoader? = null): String {
        try {
            val loader = classLoader ?: return readThemeFromSettings(context)
            val sdk = Class.forName("com.iflytek.aistudy.theme.ThemeSDK", false, loader)
            val inst = sdk.getDeclaredField("INSTANCE").get(null)
            val info = sdk.getMethod("getCurrentActiveThemeInfo").invoke(inst)
            if (info != null) {
                val themeIdField = info::class.java.getDeclaredField("themeId")
                themeIdField.isAccessible = true
                val tid = themeIdField.get(info) as? String
                if (!tid.isNullOrEmpty() && tid != "default") return tid
            }
        } catch (_: Exception) {}
        return readThemeFromSettings(context)
    }

    private fun readThemeFromSettings(context: Context): String {
        try {
            val json = android.provider.Settings.Global.getString(
                context.contentResolver, SETTINGS_THEME_KEY)
            if (json.isNullOrBlank()) return readFallbackPref(context)
            val gson = com.google.gson.Gson()
            val map = gson.fromJson(json, Map::class.java) ?: return readFallbackPref(context)
            val tid = map["theme_id"] as? String
            if (!tid.isNullOrEmpty() && tid != "default" && tid != "origin_theme") return tid
        } catch (_: Exception) {}
        return readFallbackPref(context)
    }

    private fun readFallbackPref(context: Context): String {
        return try {
            context.getSharedPreferences("rollupvastsurges_prefs", 0)
                .getString("active_skin_id", "default") ?: "default"
        } catch (_: Exception) { "default" }
    }

    // ── 私有方法 ──

    private fun tryReadAppName(skinFile: File): String? {
        return try {
            val ctx = getDummyContext()
            val pm = ctx.packageManager
            val info = pm.getPackageArchiveInfo(skinFile.absolutePath, 0)
            if (info != null) {
                val appLabel = info.applicationInfo?.loadLabel(pm)?.toString()
                if (!appLabel.isNullOrBlank()) appLabel else null
            } else null
        } catch (_: Exception) { null }
    }

    private var dummyCtx: Context? = null
    private fun getDummyContext(): Context {
        if (dummyCtx != null) return dummyCtx!!
        try {
            val at = Class.forName("android.app.ActivityThread")
            val current = at.getMethod("currentActivityThread").invoke(null)
            dummyCtx = at.getMethod("getSystemContext").invoke(current) as Context
        } catch (_: Exception) {}
        return dummyCtx ?: throw IllegalStateException("Cannot get system context")
    }
}
