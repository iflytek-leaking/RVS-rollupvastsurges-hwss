package com.leaking.rollupvastsurges.ui

import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.overlay.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.leaking.rollupvastsurges.ui.ThemeCardManagerUI

/**
 * 卷巨澜卡牌与主题管理器 — 独立的 UI 界面。
 *
 * 两个 Tab：
 *   主题管理 — 浏览/导入/切换主题
 *   卡牌管理 — 浏览/导入/删除 lkcard 卡牌
 */
class ThemeCardActivity : ComponentActivity() {

    /** 从 Intent 解析的待安装 .lkcard 路径 */
    private var pendingLkCardPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingLkCardPath = parseLkCardIntent(intent)

        setContent {
            val controller = remember {
                ThemeController(
                    if (Build.VERSION.SDK_INT >= 31) ColorSchemeMode.MonetSystem
                    else ColorSchemeMode.System,
                    keyColor = androidx.compose.ui.graphics.Color(0xFF3482FF)
                )
            }
            MiuixTheme(controller = controller) {
                ThemeCardScreen(pendingLkCardPath = pendingLkCardPath)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        pendingLkCardPath = parseLkCardIntent(intent)
    }

    private fun parseLkCardIntent(intent: android.content.Intent?): String? {
        if (intent == null) return null
        val uri: android.net.Uri? = when (intent.action) {
            android.content.Intent.ACTION_VIEW -> intent.data
            android.content.Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            }
            else -> null
        }
        if (uri == null) return null

        // 检测 .lkcard：优先检查 MIME，其次 path 后缀，最后 lastPathSegment
        val mime = intent.type?.lowercase() ?: ""
        val path = uri.path?.lowercase() ?: ""
        val lastSeg = uri.lastPathSegment?.lowercase() ?: ""
        val isLkCard = path.endsWith(".lkcard") || lastSeg.endsWith(".lkcard") ||
            mime in listOf("application/zip", "application/octet-stream") && lastSeg.contains("lkcard")

        if (!isLkCard) return null

        return try {
            val tempDir = java.io.File(cacheDir, "lkcards_import")
            tempDir.mkdirs()
            // 清理旧临时文件
            tempDir.listFiles()?.forEach { it.delete() }
            val fileName = uri.lastPathSegment?.substringBeforeLast(".") ?: "card_${System.currentTimeMillis()}"
            val tempFile = java.io.File(tempDir, "${fileName}.lkcard")
            tempFile.delete()
            contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (tempFile.exists()) tempFile.absolutePath else null
        } catch (_: Exception) {
            // 异常时清理临时目录
            try { java.io.File(cacheDir, "lkcards_import").deleteRecursively() } catch (_: Exception) {}
            null
        }
    }

    private fun cacheDir() = java.io.File(filesDir, "lkcache").also { it.mkdirs() }
}

enum class CardTab(val label: String, val icon: ImageVector) {
    THEMES("主题", Icons.Outlined.Palette),
    CARDS("卡牌", Icons.Outlined.Widgets)
}

@Composable
fun ThemeCardScreen(pendingLkCardPath: String? = null) {
    var selectedTab by remember { mutableStateOf(CardTab.THEMES) }
    var pendingCard by remember { mutableStateOf(pendingLkCardPath) }

    // 如果有待安装的 .lkcard, 自动切换到卡牌 Tab 并打开安装对话框
    LaunchedEffect(pendingCard) {
        if (pendingCard != null) {
            selectedTab = CardTab.CARDS
        }
    }

    Scaffold(
        containerColor = MiuixTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = "卷巨澜 · 卡牌与主题",
                color = MiuixTheme.colorScheme.surface
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Tab 栏
            TabRow(
                tabs = CardTab.entries.map { it.label },
                selectedTabIndex = selectedTab.ordinal,
                onTabSelected = { selectedTab = CardTab.entries[it] }
            )

            // Tab 内容
            when (selectedTab) {
                CardTab.THEMES -> ThemeCardManagerUI()
                CardTab.CARDS -> CardManagerUI(pendingLkCardPath = pendingCard, onConsumed = { pendingCard = null })
            }
        }
    }
}
