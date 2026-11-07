package com.leaking.rollupvastsurges.hook

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

/**
 * 卷巨澜 .lkcard 卡牌包管理器
 *
 * 不引用任何 TyeLauncher 类，全部通过返回 Map 让调用方反射处理。
 */
object LkCardManager {

    private const val TAG = "LkCardManager"
    private const val CARDS_DIR = "cards"
    private const val CARDS_CACHE = "cache/cards"

    private val gson = Gson()

    /**
     * 卡牌元数据的数据类（不依赖 TyeLauncher 的 BizCardMetaInner）
     */
    data class CardMeta(
        val className: String,
        val indicatorName: String,
        val indicatorNameEn: String,
        val cardName: String,
        val statPageName: String,
        val extra: String,
        val virtual: Boolean = false,
        val handleScrollConflict: Boolean = false
    )

    /**
     * 扫描并加载所有已安装的 .lkcard
     * @return CardMeta 列表，调用方通过反射设置到 BizCardMetaInner
     */
    fun loadInstalledCards(context: Context): List<CardMeta> {
        val result = mutableListOf<CardMeta>()
        val seenIds = mutableSetOf<String>()
        val cacheBase = getCacheDir(context) ?: return result

        // 扫描 TyeLauncher 自身 + Xposed 模块的 cards 目录
        val searchDirs = mutableListOf<File>()
        getCardsDir(context)?.let { searchDirs.add(it) }
        // 也扫描模块的 cards 目录（模块 UI 安装的 .lkcard 存在这里）
        try {
            val moduleCtx = context.createPackageContext("com.leaking.rollupvastsurges", 0)
            val moduleBase = moduleCtx.getExternalFilesDir(null)?.parentFile
            if (moduleBase != null) searchDirs.add(File(moduleBase, "cards"))
        } catch (_: Exception) {}

        for (cardsDir in searchDirs) {
            if (!cardsDir.exists()) continue
            val lkcardFiles = cardsDir.listFiles { f -> f.extension == "lkcard" } ?: continue
            for (lkFile in lkcardFiles) {
                try {
                    val cardId = readCardId(lkFile) ?: lkFile.nameWithoutExtension
                    if (cardId in seenIds) continue
                    seenIds.add(cardId)

                    val cacheDir = File(cacheBase, cardId)

                // 需要解压?
                if (needsExtract(lkFile, cacheDir)) {
                    cacheDir.deleteRecursively()
                    extractLkCard(lkFile, cacheDir)
                }

                val metaFile = File(cacheDir, "card.json")
                if (!metaFile.exists()) continue

                val lkMeta = gson.fromJson(metaFile.readText(), Map::class.java)
                if (lkMeta == null) continue

                val indicatorName = lkMeta["indicatorName"] as? String ?: continue
                val meta = CardMeta(
                    className = "com.leaking.rollupvastsurges.hook.LkWebViewCard",
                    indicatorName = indicatorName,
                    indicatorNameEn = (lkMeta["indicatorNameEn"] as? String) ?: indicatorName,
                    cardName = (lkMeta["cardName"] as? String) ?: cardId,
                    statPageName = (lkMeta["statPageName"] as? String) ?: "LkCard_$cardId",
                    extra = gson.toJson(mapOf("lkcard_dir" to cacheDir.absolutePath)),
                    virtual = (lkMeta["virtual"] as? Boolean) ?: false,
                    handleScrollConflict = (lkMeta["handle_scroll_conflict"] as? Boolean) ?: false
                )
                result.add(meta)
                Log.w(TAG, "已加载 .lkcard: $cardId")
            } catch (e: Exception) {
                Log.w(TAG, "加载 .lkcard 失败: ${lkFile.name}: ${e.message}")
            }
        }
        }
        return result
    }

    /**
     * 从 .lkcard 中读取 cardId
     */
    private fun readCardId(lkFile: File): String? {
        return try {
            val zis = ZipInputStream(FileInputStream(lkFile))
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "card.json") {
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    zis.close()
                    val map = gson.fromJson(text, Map::class.java)
                    return (map?.get("id") as? String) ?: (map?.get("cardName") as? String)
                }
                entry = zis.nextEntry
            }
            zis.close()
            null
        } catch (_: Exception) { null }
    }

    private fun needsExtract(lkFile: File, cacheDir: File): Boolean {
        if (!cacheDir.exists()) return true
        val marker = File(cacheDir, ".extracted")
        return !marker.exists() || marker.lastModified() < lkFile.lastModified()
    }

    private fun extractLkCard(lkFile: File, targetDir: File) {
        targetDir.mkdirs()
        val zis = ZipInputStream(FileInputStream(lkFile))
        var entry = zis.nextEntry
        while (entry != null) {
            if (!entry.isDirectory) {
                val outFile = File(targetDir, entry.name)
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { out -> zis.copyTo(out) }
            }
            entry = zis.nextEntry
        }
        zis.close()
        File(targetDir, ".extracted").writeText(System.currentTimeMillis().toString())
    }

    private fun getCardsDir(context: Context): File? {
        return try {
            val base = context.getExternalFilesDir(null)?.parentFile
            if (base != null) File(base, CARDS_DIR) else null
        } catch (_: Exception) { null }
    }

    fun getOrCreateCardsDir(context: Context): File? {
        val dir = getCardsDir(context) ?: return null
        if (!dir.exists()) dir.mkdirs()
        return if (dir.exists()) dir else null
    }

    private fun getCacheDir(context: Context): File? {
        return try {
            val base = context.getExternalFilesDir(null)?.parentFile
            if (base != null) File(base, CARDS_CACHE) else null
        } catch (_: Exception) { null }
    }

    fun clearCache(context: Context) {
        val cacheDir = getCacheDir(context) ?: return
        if (cacheDir.exists()) cacheDir.deleteRecursively()
    }

    /**
     * 获取已安装的 .lkcard 列表（供 UI 显示）
     */
    fun getInstalledPackages(context: Context): List<Map<String, String>> {
        val cacheBase = getCacheDir(context) ?: return emptyList()
        if (!cacheBase.exists()) return emptyList()
        return cacheBase.listFiles()?.mapNotNull { dir ->
            val pkgFile = File(dir, "package.json")
            if (!pkgFile.exists()) return@mapNotNull null
            try {
                val map = gson.fromJson(pkgFile.readText(), Map::class.java)
                map?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() }
            } catch (_: Exception) { null }
        } ?: emptyList()
    }
}
