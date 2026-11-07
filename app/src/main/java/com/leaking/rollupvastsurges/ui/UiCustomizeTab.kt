package com.leaking.rollupvastsurges.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.leaking.rollupvastsurges.hook.IconPackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 美化 Tab — 集中收纳 UI 美化组件（QS 磁贴/面板 + 通知栏/最近任务背景 + 主题/图标包）。
 * 侧栏导航"美化"入口。pref 读取走 HookEngine 的 Settings.Global 跨进程机制。
 */
@Composable
fun UiCustomizeTab(ctx: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text("界面美化",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        item { SectionTitle(text = "QS 磁贴美化") }
        item {
            SettingsGroup {
                QSColorRow(ctx, "磁贴激活背景色", "qs_tile_bg_active")
                QSColorRow(ctx, "磁贴未激活背景色", "qs_tile_bg_inactive")
                QSColorRow(ctx, "磁贴激活图标色", "qs_tile_icon_active")
                QSColorRow(ctx, "磁贴未激活图标色", "qs_tile_icon_inactive")
                QSColorRow(ctx, "磁贴激活标签色", "qs_tile_label_active")
                QSColorRow(ctx, "亮度条颜色", "qs_brightness_slider_color")
                QSCornerRadiusRow(ctx)
                QSTileColumnsRow(ctx)
                QSPanelCornerRadiusRow(ctx)
                QSTileLabelRow(ctx)
                QSPanelBlurRow(ctx)
                QSHeaderImageRow(ctx, snackbar, scope)
                Text("修改后下拉刷新或重启 SystemUI 生效",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { SectionTitle(text = "通知栏美化") }
        item {
            SettingsGroup {
                NotifBgImageRow(ctx, snackbar, scope)
                NotifBgBlurRow(ctx)
                RecentsBgImageRow(ctx, snackbar, scope)
                Text("背景图仅替换通知栏/最近任务（锁屏保持系统壁纸，独立显示）；" +
                    "模糊用 GPU RenderEffect，改后重启 SystemUI 生效",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { SectionTitle(text = "主题美化") }
        item {
            // 卡牌与主题管理器入口
            Surface(
                onClick = {
                    val intent = android.content.Intent(ctx, ThemeCardActivity::class.java)
                    ctx.startActivity(intent)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MiuixTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Palette, contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("卡牌与主题",
                            color = MiuixTheme.colorScheme.onSecondaryContainer)
                        Text("管理桌面插件和主题皮肤",
                            color = MiuixTheme.colorScheme.onSecondaryContainer)
                    }
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { SectionTitle(text = "自定义桌面 (TyeLauncher)") }
        item {
            SettingsGroup {
                SwitchRow("桌面模式 (Workspace)", "切换到 Workspace 桌面模式（非 ViewPager 卡牌），需重启桌面", "tyelauncher_workspace_mode", false)
                SwitchRow("注入自定义卡牌", "从 ExternalFilesDir 或 /sdcard/ 加载自定义卡牌配置", "tyelauncher_custom_cards", false)
                // 桌面布局选择器（OverlayDropdownPreference 自带 title，无需额外 Text）
                LauncherLayoutSelector(ctx)
                // 功能开关折叠区域
                var showFlags by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { showFlags = !showFlags }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (showFlags) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceSecondary)
                    Spacer(Modifier.width(8.dp))
                    Text("桌面功能开关", modifier = Modifier.weight(1f))
                }
                if (showFlags) {
                    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                        val flags = listOf(
                            "flag_support_dock" to "Dock栏",
                            "flag_support_jzxos" to "极战线OS模式",
                            "flag_support_new_launcher" to "新版桌面",
                            "flag_support_double_launcher" to "双桌面模式",
                            "flag_support_theme" to "主题换肤",
                            "flag_show_study_plan" to "学习计划入口",
                            "flag_show_wrongbook" to "错题本入口",
                            "flag_show_ebook_shop" to "电子书商店",
                            "flag_support_talktalk" to "英语TalkTalk",
                            "flag_support_raz" to "RAZ英语",
                            "flag_support_ai_exam" to "AI数学考试",
                            "flag_support_ai_help" to "AI学伴助手",
                            "flag_support_sim" to "SIM卡功能",
                            "flag_support_location" to "GPS定位",
                            "flag_dock_hide_paint" to "Dock隐藏绘画",
                            "flag_dock_hide_coding" to "Dock隐藏编程助手",
                            "flag_wallpaper_kg" to "幼儿园壁纸",
                            "flag_concentration" to "专注力训练",
                            "flag_hide_kepu" to "隐藏科普问答",
                            "flag_hide_flash" to "隐藏闪卡功能",
                        )
                        flags.forEach { (key: String, label: String) ->
                            var value by remember { mutableStateOf(com.leaking.rollupvastsurges.hook.HookEngine.readStr(key, "")) }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    value = if (value == "true") "false" else "true"
                                    // 跨进程双写: launcher 进程读不到模块 SP，必须写 Settings.Global
                                    setPref(ctx, key, value)
                                }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(state = if (value == "true") ToggleableState.On else ToggleableState.Off, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { IconPackSelectorRow() }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            SettingsGroup {
                SwitchRow("壁纸跟随系统", "阻止 TyeLauncher 加载场景背景图，让系统壁纸透传到底层桌面", "tyelauncher_follow_system_wallpaper", false)
                SwitchRow("自定义背景图", "替换场景背景为 custom_launcher_bg 资源", "tyelauncher_custom_bg", false)
            }
        }
    }
}

private val QS_PRESET_COLORS = listOf(
    0xFF3482FF.toInt() to "MIUI蓝",
    0xFF000000.toInt() to "黑色",
    0xFFFFFFFF.toInt() to "白色",
    0xFF4CAF50.toInt() to "绿色",
    0xFFFFC107.toInt() to "黄色",
    0xFFF44336.toInt() to "红色",
    0xFF9C27B0.toInt() to "紫色",
    0xFF009688.toInt() to "青色",
)

@Composable
fun QSColorRow(ctx: Context, title: String, prefKey: String) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var current by remember { mutableStateOf(prefInt(ctx, prefKey, 0)) }
    val prefsKey = prefKey

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = MiuixTheme.colorScheme.onSurface)
            Text(if (current == 0) "默认" else String.format("#%08X", current),
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
        // 色块预览 + 点击清空
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (current == 0) MiuixTheme.colorScheme.surfaceVariant else Color(current))
                .clickable {
                    sp.edit().putInt(prefsKey, 0).apply()
                    setPref(ctx, prefsKey, "0")
                    current = 0
                }
                .border(1.dp, MiuixTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.width(8.dp))
        // 色板
        Row {
            QS_PRESET_COLORS.forEach { (color, _) ->
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .clickable {
                            sp.edit().putInt(prefsKey, color).apply()
                            setPref(ctx, prefsKey, color.toString())
                            current = color
                        }
                        .border(if (current == color) 2.dp else 0.dp,
                            MiuixTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

@Composable
fun QSCornerRadiusRow(ctx: Context) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var radius by remember { mutableStateOf(prefInt(ctx, "qs_tile_corner_radius", 0)) }
    val options = listOf(0 to "默认", 4 to "4", 8 to "8", 16 to "16", 24 to "24")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("磁贴圆角 (dp)", modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface)
        options.forEach { (value, label) ->
            Text(
                label,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (radius == value) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant)
                    .clickable {
                        sp.edit().putInt("qs_tile_corner_radius", value).apply()
                        setPref(ctx, "qs_tile_corner_radius", value.toString())
                        radius = value
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (radius == value) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

// ── QS 磁贴列数（横屏/竖屏） ──
@Composable
fun QSTileColumnsRow(ctx: Context) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var portrait by remember { mutableStateOf(prefInt(ctx, "qs_tile_columns_portrait", 0)) }
    var landscape by remember { mutableStateOf(prefInt(ctx, "qs_tile_columns_landscape", 0)) }
    val options = listOf(0 to "默认", 3 to "3", 4 to "4", 5 to "5", 6 to "6", 7 to "7", 8 to "8")

    @Composable
    fun ColumnPicker(title: String, key: String, value: Int, onPick: (Int) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, modifier = Modifier.weight(1f),
                color = MiuixTheme.colorScheme.onSurface)
            options.forEach { (v, label) ->
                Text(
                    label,
                    modifier = Modifier
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (value == v) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceVariant)
                        .clickable {
                            sp.edit().putInt(key, v).apply()
                            setPref(ctx, key, v.toString())
                            onPick(v)
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    color = if (value == v) MiuixTheme.colorScheme.onPrimary
                        else MiuixTheme.colorScheme.onSurface
                )
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("磁贴列数",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            color = MiuixTheme.colorScheme.onSurface)
        ColumnPicker("竖屏", "qs_tile_columns_portrait", portrait) { portrait = it }
        ColumnPicker("横屏", "qs_tile_columns_landscape", landscape) { landscape = it }
        Text("横屏学习机建议 6-8 列，竖屏 4-5 列",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            color = MiuixTheme.colorScheme.onSurfaceSecondary)
    }
}

// ── QS 面板圆角 ──
@Composable
fun QSPanelCornerRadiusRow(ctx: Context) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var radius by remember { mutableStateOf(prefInt(ctx, "qs_panel_corner_radius", 0)) }
    val options = listOf(0 to "默认", 20 to "20", 28 to "28", 36 to "36", 44 to "44")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("面板圆角 (dp)", modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface)
        options.forEach { (value, label) ->
            Text(
                label,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (radius == value) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant)
                    .clickable {
                        sp.edit().putInt("qs_panel_corner_radius", value).apply()
                        setPref(ctx, "qs_panel_corner_radius", value.toString())
                        radius = value
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (radius == value) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

// ── QS 磁贴标签显示开关 ──
@Composable
fun QSTileLabelRow(ctx: Context) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var show by remember { mutableStateOf(sp.getBoolean("qs_tile_show_label", true)) }
    val options = listOf(true to "显示", false to "隐藏")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("磁贴标签", modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface)
        options.forEach { (value, label) ->
            Text(
                label,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (show == value) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant)
                    .clickable {
                        sp.edit().putBoolean("qs_tile_show_label", value).apply()
                        setPref(ctx, "qs_tile_show_label", if (value) "1" else "0")
                        show = value
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (show == value) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

// ── QS 面板背景模糊 ──
@Composable
fun QSPanelBlurRow(ctx: Context) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var blur by remember { mutableStateOf(prefInt(ctx, "qs_panel_blur_radius", 0)) }
    val options = listOf(0 to "关闭", 20 to "20", 40 to "40", 60 to "60", 80 to "80")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("QS 背景模糊 (dp)", color = MiuixTheme.colorScheme.onSurface)
            Text("模糊面板后的壁纸内容，需重启 SystemUI",
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
        options.forEach { (value, label) ->
            Text(
                label,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (blur == value) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant)
                    .clickable {
                        sp.edit().putInt("qs_panel_blur_radius", value).apply()
                        setPref(ctx, "qs_panel_blur_radius", value.toString())
                        blur = value
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (blur == value) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

// ── QS 表头自定义背景图片 ──
@Composable
fun QSHeaderImageRow(ctx: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var path by remember { mutableStateOf(sp.getString("qs_header_image_path", "") ?: "") }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val dest = java.io.File(ctx.filesDir, "qs_header.png")
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                sp.edit().putString("qs_header_image_path", dest.absolutePath).apply()
                setPref(ctx, "qs_header_image_path", dest.absolutePath)
                path = dest.absolutePath
                scope.launch { snackbar.showSnackbar("表头图片已设置，重启 SystemUI 生效") }
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar("图片复制失败: ${e.message}") }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("QS 表头背景图片", color = MiuixTheme.colorScheme.onSurface)
            Text(if (path.isEmpty()) "选择图片填充 QS 表头" else "已设置: ${java.io.File(path).name}",
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
        Text(if (path.isEmpty()) "选择" else "更换",
            color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { launcher.launch(arrayOf("image/*")) })
        if (path.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text("清除", color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.clickable {
                    sp.edit().remove("qs_header_image_path").apply()
                    setPref(ctx, "qs_header_image_path", "")
                    path = ""
                })
        }
    }
}

// ── 通知栏/锁屏背景图 ──
@Composable
fun NotifBgImageRow(ctx: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var path by remember { mutableStateOf(sp.getString("keyguard_wallpaper_path", "") ?: "") }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val dest = java.io.File(ctx.filesDir, "notif_bg.png")
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                sp.edit().putString("keyguard_wallpaper_path", dest.absolutePath).apply()
                setPref(ctx, "keyguard_wallpaper_path", dest.absolutePath)
                path = dest.absolutePath
                scope.launch { snackbar.showSnackbar("背景图已设置，重启 SystemUI 生效") }
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar("图片复制失败: ${e.message}") }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("通知栏背景图", color = MiuixTheme.colorScheme.onSurface)
            Text(if (path.isEmpty()) "替换锁屏+通知栏壁纸层"
                else "已设置: ${java.io.File(path).name}",
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
        Text(if (path.isEmpty()) "选择" else "更换",
            color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { launcher.launch(arrayOf("image/*")) })
        if (path.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text("清除", color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.clickable {
                    sp.edit().remove("keyguard_wallpaper_path").apply()
                    setPref(ctx, "keyguard_wallpaper_path", "")
                    path = ""
                })
        }
    }
}

// ── 通知栏/锁屏背景模糊 ──
@Composable
fun NotifBgBlurRow(ctx: Context) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var blur by remember { mutableStateOf(prefInt(ctx, "notif_bg_blur", 0)) }
    val options = listOf(0 to "关闭", 20 to "20", 40 to "40", 60 to "60", 80 to "80")

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("背景模糊 (dp)", modifier = Modifier.weight(1f),
            color = MiuixTheme.colorScheme.onSurface)
        options.forEach { (value, label) ->
            Text(
                label,
                modifier = Modifier
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (blur == value) MiuixTheme.colorScheme.primary
                        else MiuixTheme.colorScheme.surfaceVariant)
                    .clickable {
                        sp.edit().putInt("notif_bg_blur", value).apply()
                        setPref(ctx, "notif_bg_blur", value.toString())
                        blur = value
                    }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (blur == value) MiuixTheme.colorScheme.onPrimary
                    else MiuixTheme.colorScheme.onSurface
            )
        }
    }
}

// ── 最近任务背景图 ──
@Composable
fun RecentsBgImageRow(ctx: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var path by remember { mutableStateOf(sp.getString("recents_bg_path", "") ?: "") }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val dest = java.io.File(ctx.filesDir, "recents_bg.png")
            try {
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                sp.edit().putString("recents_bg_path", dest.absolutePath).apply()
                setPref(ctx, "recents_bg_path", dest.absolutePath)
                path = dest.absolutePath
                scope.launch { snackbar.showSnackbar("最近任务背景图已设置") }
            } catch (e: Exception) {
                scope.launch { snackbar.showSnackbar("图片复制失败: ${e.message}") }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("最近任务背景图", color = MiuixTheme.colorScheme.onSurface)
            Text(if (path.isEmpty()) "替换 RecentsView 背景（覆盖壁纸层）"
                else "已设置: ${java.io.File(path).name}",
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
        Text(if (path.isEmpty()) "选择" else "更换",
            color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable { launcher.launch(arrayOf("image/*")) })
        if (path.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text("清除", color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.clickable {
                    sp.edit().remove("recents_bg_path").apply()
                    setPref(ctx, "recents_bg_path", "")
                    path = ""
                })
        }
    }
}
@Composable
fun IconPackSelectorRow() {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var activePack by remember { mutableStateOf(sp.getString("selected_icon_pack", "") ?: "") }
    var showDialog by remember { mutableStateOf(false) }
    var packs by remember { mutableStateOf<List<IconPackManager.IconPackInfo>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun saveIconPack(pkg: String) {
        activePack = pkg
        sp.edit().putString("selected_icon_pack", pkg).apply()
        // 跨进程双写: launcher 进程读不到模块 SP，必须写 Settings.Global
        android.provider.Settings.Global.putString(ctx.contentResolver,
            "rollupvastsurges_pref_selected_icon_pack", pkg)
        // 选择图标包时自动启用 hook，清空时禁用
        sp.edit().putBoolean("hook_icon_pack", pkg.isNotEmpty()).apply()
        android.provider.Settings.Global.putString(ctx.contentResolver,
            "rollupvastsurges_pref_hook_icon_pack", if (pkg.isNotEmpty()) "1" else "0")
        IconPackManager.clearCache()
    }

    fun doScan() {
        scanning = true
        scope.launch(Dispatchers.IO) {
            val result = IconPackManager.scanIconPacks(pm)
            withContext(Dispatchers.Main) {
                packs = result; scanning = false; showDialog = true
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = !scanning) {
                doScan()
            }.defaultMinSize(minHeight = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.GridView, contentDescription = null,
                tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("图标包",
                    color = MiuixTheme.colorScheme.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(
                    when {
                        scanning -> "扫描中..."
                        activePack.isEmpty() -> "默认图标"
                        else -> activePack
                    },
                    color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
            Button(onClick = { doScan() }, enabled = !scanning) {
                Text(if (scanning) "..." else "选择")
            }
        }
    }

    OverlayDialog(
        show = showDialog,
        title = "选择图标包",
        onDismissRequest = { showDialog = false },
    ) {
        if (packs.isNotEmpty()) {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            saveIconPack("")
                            android.widget.Toast.makeText(ctx, "图标: 默认", android.widget.Toast.LENGTH_SHORT).show()
                            showDialog = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (activePack.isEmpty()) MiuixTheme.colorScheme.secondaryContainer
                            else MiuixTheme.colorScheme.surface
                    ) {
                        Text("默认图标", modifier = Modifier.padding(16.dp))
                    }
                }
                items(packs) { pack ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable {
                            saveIconPack(pack.packageName)
                            android.widget.Toast.makeText(ctx,
                                "图标: ${pack.label} (${pack.iconCount}个)",
                                android.widget.Toast.LENGTH_SHORT).show()
                            showDialog = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = if (pack.packageName == activePack)
                            MiuixTheme.colorScheme.secondaryContainer
                            else MiuixTheme.colorScheme.surface
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(pack.label)
                            Text("${pack.iconCount} 图标 · ${pack.packageName}",
                                color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                    }
                }
            }
        } else {
            Text("未检测到图标包\n\n图标包需声明:\n" +
                 "com.android.launcher.action.INSTALL_ICON_PACK")
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(text = "关闭", onClick = { showDialog = false })
        }
    }
}
