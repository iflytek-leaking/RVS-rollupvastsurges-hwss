package com.leaking.rollupvastsurges.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
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
import com.leaking.rollupvastsurges.hook.SkinUtils
import java.io.File

/**
 * 主题管理 UI — 扫描/导入/切换主题。
 */
@Composable
fun ThemeCardManagerUI() {
    val ctx = LocalContext.current
    var themeList by remember { mutableStateOf<List<SkinUtils.SkinInfo>>(emptyList()) }
    var currentSkinId by remember { mutableStateOf("default") }
    var showImportDialog by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    // 加载数据
    LaunchedEffect(Unit) {
        themeList = SkinUtils.scanInstalledSkins(ctx)
        currentSkinId = SkinUtils.getCurrentSkinId(ctx)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 当前主题卡片
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            cornerRadius = 16.dp,
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Palette, contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("当前主题",
                        color = MiuixTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (currentSkinId == "default") "默认主题" else currentSkinId,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        // 已安装主题列表
        Text("已安装主题",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                InfiniteProgressIndicator()
            }
        } else if (themeList.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("未检测到已安装的主题", color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(themeList) { skin ->
                    ThemeListItem(skin = skin, isActive = skin.id == currentSkinId,
                        onSwitch = {
                            SkinUtils.switchSkin(ctx, skin.id)
                            currentSkinId = skin.id
                            // 检查是否有冲突功能开启
                            val prefs = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
                            val wallpaperFollow = prefs.getBoolean("tyelauncher_follow_system_wallpaper", false)
                            val customBg = prefs.getBoolean("tyelauncher_custom_bg", false)
                            if (wallpaperFollow || customBg) {
                                val conflicts = mutableListOf<String>()
                                if (wallpaperFollow) conflicts.add("壁纸跟随系统")
                                if (customBg) conflicts.add("自定义背景图")
                                Toast.makeText(ctx, "提示: ${conflicts.joinToString("、")}已开启，可能覆盖主题背景",
                                    Toast.LENGTH_LONG).show()
                            }
                            Toast.makeText(ctx, "已切换: ${skin.displayName}", Toast.LENGTH_SHORT).show()
                        })
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
                onClick = { showImportDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.FileOpen, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("导入 .skin")
            }
            Button(
                onClick = {
                    SkinUtils.switchSkin(ctx, "default")
                    currentSkinId = "default"
                    Toast.makeText(ctx, "已恢复默认主题", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.Restore, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("恢复默认")
            }
        }
    }

    // 导入对话框
    ImportSkinDialog(show = showImportDialog, onDismiss = { showImportDialog = false },
        onImported = { themeId ->
            if (themeId != null) {
                themeList = SkinUtils.scanInstalledSkins(ctx)
                currentSkinId = SkinUtils.getCurrentSkinId(ctx)
            }
            showImportDialog = false
        })
}

@Composable
private fun ThemeListItem(
    skin: SkinUtils.SkinInfo,
    isActive: Boolean,
    onSwitch: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(enabled = !isActive) { onSwitch() },
        shape = RoundedCornerShape(8.dp),
        color = if (isActive) MiuixTheme.colorScheme.secondaryContainer
                else MiuixTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isActive) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isActive) MiuixTheme.colorScheme.primary
                       else MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(skin.displayName)
                Row {
                    if (skin.isThirdParty) {
                        Text("第三方 · ",
                            color = MiuixTheme.colorScheme.tertiaryContainer)
                    }
                    Text("${skin.size / 1024}KB",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }
            if (isActive) {
                Text("当前",
                    color = MiuixTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ImportSkinDialog(show: Boolean, onDismiss: () -> Unit, onImported: (String?) -> Unit) {
    val ctx = LocalContext.current
    var selectedPath by remember { mutableStateOf("") }
    var themeId by remember { mutableStateOf("") }

    OverlayDialog(
        show = show,
        title = "导入第三方主题",
        onDismissRequest = onDismiss,
    ) {
        Column {
            Text("将 .skin 或官方主题 .zip 放入 /sdcard/themes/ 目录",
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
            Spacer(Modifier.height(8.dp))
            TextField(
                value = selectedPath,
                onValueChange = { selectedPath = it },
                label = ".skin 文件路径",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            TextField(
                value = themeId,
                onValueChange = { themeId = it },
                label = "主题 ID（可选，默认用文件名）",
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(text = "取消", onClick = onDismiss)
                TextButton(text = "导入", onClick = {
                    val source = if (selectedPath.isNotEmpty()) {
                        File(selectedPath)
                    } else {
                        val dir = File("/sdcard/themes")
                        dir.listFiles { f -> f.extension == "skin" || f.extension == "zip" }?.firstOrNull()
                    }
                    if (source != null && source.isFile) {
                        val result = when {
                            source.extension == "zip" -> {
                                val r = SkinUtils.installThemeZip(ctx, source)
                                if (r != null) { Toast.makeText(ctx, "导入成功: ${r.displayName}", Toast.LENGTH_SHORT).show(); r.themeId } else null
                            }
                            else -> {
                                val id = SkinUtils.installSkin(ctx, source)
                                if (id != null) { Toast.makeText(ctx, "导入成功: $id", Toast.LENGTH_SHORT).show(); id } else null
                            }
                        }
                        if (result != null) onImported(result)
                        else Toast.makeText(ctx, "导入失败", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(ctx, "找不到有效的 .skin 或 .zip 文件", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
    }
}
