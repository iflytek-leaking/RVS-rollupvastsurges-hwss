package com.leaking.rollupvastsurges.ui

import android.widget.Toast
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.overlay.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * lkcard 卡牌管理 UI — 浏览/导入/删除插件卡牌。
 */
@Composable
fun CardManagerUI(pendingLkCardPath: String? = null, onConsumed: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    var installedCards by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showImportHelp by remember { mutableStateOf(false) }
    var pendingInstallPath by remember { mutableStateOf(pendingLkCardPath) }

    // 处理待安装的 .lkcard 文件
    LaunchedEffect(pendingInstallPath) {
        val path = pendingInstallPath ?: return@LaunchedEffect
        pendingInstallPath = null
        onConsumed?.invoke()
        try {
            val tempFile = java.io.File(path)
            if (!tempFile.exists()) return@LaunchedEffect
            // 解析 card.json
            val zis = java.util.zip.ZipInputStream(java.io.FileInputStream(tempFile))
            var cardName: String? = null
            var cardId: String? = null
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "card.json") {
                    val text = zis.readBytes().decodeToString()
                    val gson = com.google.gson.Gson()
                    val map = gson.fromJson(text, Map::class.java)
                    if (map != null) {
                        cardName = (map["indicatorName"] as? String) ?: (map["cardName"] as? String)
                        cardId = (map["id"] as? String) ?: (map["cardName"] as? String)
                    }
                    break
                }
                entry = zis.nextEntry
            }
            zis.close()
            // 复制到 cards 目录
            val cardsDir = getLkCardsDir(ctx)
            if (cardsDir != null && cardId != null) {
                val target = java.io.File(cardsDir, "$cardId.lkcard")
                tempFile.copyTo(target, overwrite = true)
                clearLkCache(ctx)
                installedCards = getInstalledLkPkgs(ctx)
                android.widget.Toast.makeText(ctx, "卡牌已安装: $cardName", android.widget.Toast.LENGTH_SHORT).show()
            }
            // 清理临时文件
            try { tempFile.delete() } catch (_: Exception) {}
            try { tempFile.parentFile?.deleteRecursively() } catch (_: Exception) {}
        } catch (_: Exception) {}
    }

    // 加载已安装卡牌
    LaunchedEffect(Unit) {
        installedCards = getInstalledLkPkgs(ctx)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 统计信息
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            cornerRadius = 16.dp,
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.tertiaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Widgets, contentDescription = null,
                    tint = MiuixTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("已安装卡牌",
                        color = MiuixTheme.colorScheme.onTertiaryContainer)
                    Text("${installedCards.size} 个插件",
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onTertiaryContainer)
                }
            }
        }

        // 已安装列表
        Text("已安装的卡牌",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator()
            }
        } else if (installedCards.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无已安装的卡牌",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("将 .lkcard 文件放到外部存储的 cards/ 目录",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(installedCards) { card ->
                    LkCardListItem(
                        name = card["name"] ?: card["id"] ?: "未知",
                        version = card["version"] ?: "",
                        author = card["author"] ?: "",
                        onDelete = {
                            val id = card["id"] ?: return@LkCardListItem
                            val cacheDir = getLkCacheDir(ctx)
                            if (cacheDir != null) {
                                File(cacheDir, id).deleteRecursively()
                                installedCards = getInstalledLkPkgs(ctx)
                                Toast.makeText(ctx, "已删除: $id", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // 底部操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { showImportHelp = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("安装卡牌")
            }
            Button(
                onClick = {
                    clearLkCache(ctx)
                    installedCards = getInstalledLkPkgs(ctx)
                    Toast.makeText(ctx, "缓存已清空", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.CleaningServices, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("清空缓存")
            }
        }
    }

    // 导入帮助对话框
    OverlayDialog(
        show = showImportHelp,
        title = "安装 .lkcard 卡牌",
        onDismissRequest = { showImportHelp = false },
    ) {
        Column {
            Text("将 .lkcard 文件放入:")
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MiuixTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "/sdcard/Android/data/com.toycloud.launcher/files/cards/",
                    modifier = Modifier.padding(12.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("或通过文件管理器用「分享」发送 .lkcard 文件到本应用。",
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
            Spacer(Modifier.height(8.dp))
            Text("安装后需重启 TyeLauncher 桌面方可生效。",
                color = MiuixTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(text = "知道了", onClick = { showImportHelp = false })
            }
        }
    }
}

@Composable
private fun LkCardListItem(
    name: String,
    version: String,
    author: String,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp),
        color = MiuixTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Extension, contentDescription = null,
                tint = MiuixTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name)
                if (version.isNotEmpty() || author.isNotEmpty()) {
                    Text(listOfNotNull(
                        version.ifEmpty { null },
                        author.ifEmpty { null }
                    ).joinToString(" · "),
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除",
                    tint = MiuixTheme.colorScheme.error)
            }
        }
    }
}

private const val TYE_LAUNCHER = "com.toycloud.launcher"

// ── lkcard 工具函数 ──

/** 获取模块自身外部存储的 cards 目录 */
private fun getLkCardsDir(ctx: android.content.Context): File? {
    return try {
        val base = ctx.getExternalFilesDir(null)?.parentFile
        if (base != null) {
            val dir = File(base, "cards")
            if (!dir.exists()) dir.mkdirs()
            if (dir.exists()) dir else null
        } else null
    } catch (_: Exception) { null }
}

/** 获取模块自身外部存储的 lkcard 缓存目录 */
private fun getLkCacheDir(ctx: android.content.Context): File? {
    return try {
        val base = ctx.getExternalFilesDir(null)?.parentFile ?: return null
        File(base, "cache/cards").also { if (!it.exists()) it.mkdirs() }
    } catch (_: Exception) { null }
}

private fun getInstalledLkPkgs(ctx: android.content.Context): List<Map<String, String>> {
    val base = ctx.getExternalFilesDir(null)?.parentFile ?: return emptyList()
    val cacheDir = File(base, "cache/cards")
    if (!cacheDir.exists()) return emptyList()
    val gson = com.google.gson.Gson()
    return cacheDir.listFiles()?.mapNotNull { dir ->
        val pkgFile = File(dir, "package.json")
        if (!pkgFile.exists()) return@mapNotNull null
        try {
            @Suppress("UNCHECKED_CAST")
            val map = gson.fromJson(pkgFile.readText(), Map::class.java) as? Map<String, String>
            map
        } catch (_: Exception) { null }
    } ?: emptyList()
}

private fun clearLkCache(ctx: android.content.Context) {
    val base = ctx.getExternalFilesDir(null)?.parentFile ?: return
    val cacheDir = File(base, "cache/cards")
    if (cacheDir.exists()) cacheDir.deleteRecursively()
}
