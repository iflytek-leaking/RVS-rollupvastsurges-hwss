package com.leaking.rollupvastsurges.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.leaking.rollupvastsurges.hook.SkinUtils
import com.leaking.rollupvastsurges.hook.IconPackManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.AdsClick
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.HideSource
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.Send
// ── Miuix 组件 ──
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.preference.*
import top.yukonga.miuix.kmp.overlay.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import com.leaking.rollupvastsurges.hook.RomType
import com.leaking.rollupvastsurges.hook.detectRomType
import com.leaking.rollupvastsurges.hook.getNativePenhookStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            enableEdgeToEdge()
        } catch (_: Throwable) {}
        setContent {
            val context = LocalContext.current
            // Miuix ThemeController — Monet 动态取色 (Android 12+) / 默认 MIUI 配色
            val controller = remember {
                ThemeController(
                    if (Build.VERSION.SDK_INT >= 31) ColorSchemeMode.MonetSystem
                    else ColorSchemeMode.System,
                )
            }
            MiuixTheme(controller = controller) {
                val owner = rememberNavigationEventDispatcherOwner(parent = null)
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                    MDMDisablerApp()
                }
            }
        }
    }

    /** 读取 .lkcard 的 package.json 后弹出 MD3 安装确认窗 */
    private fun handleLkCardIntent(intent: android.content.Intent?) {
        try {
            val target = android.content.Intent(this, ThemeCardActivity::class.java)
                .setAction(intent?.action)
                .setData(intent?.data)
            intent?.extras?.let { target.putExtras(it) }
            startActivity(target)
            finish()
        } catch (_: Exception) {}
    }

    private fun performLkCardInstall(card: Any) {}
    private fun findExistingVersion(cardId: String?): String? = null
    private fun cacheDir() = java.io.File(filesDir, "lkcache").also { it.mkdirs() }
}

data class DeviceInfo(
    val model: String, val sn: String, val androidId: String,
    val account: String, val pen: String, val romLabel: String,
    val version: String, val scopeHint: String
)

private fun loadDeviceInfo(ctx: Context): DeviceInfo {
    val romType = detectRomType()
    val romLabel = when (romType) {
        RomType.A9_STANDARD, RomType.A9_HWC -> "Android 9 标准版"
        RomType.A12_STANDARD, RomType.A12_HWC -> "Android 12 HWC"
        RomType.A13_STANDARD, RomType.A13_HWC -> "Android 13 HWC"
        RomType.X35G -> "X35G / 联想"
        RomType.UNKNOWN -> "未知"
    }
    val sn = try {
        val sp = Class.forName("android.os.SystemProperties"); val get = sp.getMethod("get", String::class.java, String::class.java)
        get.invoke(null, "ro.serialno", "?") as String
    } catch (_: Exception) { "?" }
    val aid = try { Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "?" }
    catch (_: Exception) { "?" }
    val account = getAccount(ctx)
    val pen = getPen(ctx)
    val scopeHint = when (romType) {
        RomType.X35G -> "android · com.iflytek.k12aitstatistics"
        else -> "android · com.iflytek.k12aitstatistics\ncom.iflytek.hwc.service"
    }
    return DeviceInfo(
        model = "${Build.MANUFACTURER} ${Build.MODEL}", sn = sn, androidId = aid,
        account = account, pen = pen, romLabel = romLabel,
        version = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        scopeHint = scopeHint
    )
}

private fun getAccount(ctx: Context): String {
    for (uriStr in listOf("content://com.iflytek.usercenter.accounts/my_accounts",
            "content://com.iflytek.lanucher.userinfo/userinfo")) {
        try {
            val uri = android.net.Uri.parse(uriStr)
            ctx.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToLast()) {
                    for (col in listOf("username", "realname", "userid", "phone")) {
                        val idx = cur.getColumnIndex(col); if (idx < 0) continue
                        val v = cur.getString(idx)
                        if (!v.isNullOrBlank()) {
                            if (col == "username") {
                                val ri = cur.getColumnIndex("realname")
                                if (ri >= 0) { val r = cur.getString(ri); if (!r.isNullOrBlank()) return "$v ($r)" }
                            }
                            return v
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return "未登录"
}

private fun getPen(ctx: Context): String {
    try {
        val sp = Class.forName("android.os.SystemProperties"); val get = sp.getMethod("get", String::class.java, String::class.java)
        val bat = get.invoke(null, "persist.vendor.iflytek.pen.battery", "") as String
        if (bat.isNotEmpty()) return "${bat}%"
    } catch (_: Exception) {}
    for (spName in listOf("hwc_pen_sp", "bt_pen_preferences", "com.iflytek.hwc.service_preferences")) {
        try {
            val l = ctx.getSharedPreferences(spName, Context.MODE_PRIVATE).getInt("pen_battery_level", -1)
            if (l >= 0) return "${l}%"
        } catch (_: Exception) {}
    }
    return "—"
}

private fun prefBool(ctx: Context, key: String, def: Boolean) =
    ctx.getSharedPreferences("rollupvastsurges_prefs", 0).getBoolean(key, def)

// 安全读取 Int：历史版本可能用 putString 写过同 key（String），getInt 会抛
// ClassCastException 崩 UI。兜底读 String 转 Int。
internal fun prefInt(ctx: Context, key: String, def: Int): Int {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    return try {
        sp.getInt(key, def)
    } catch (_: ClassCastException) {
        sp.getString(key, null)?.toIntOrNull() ?: def
    }
}

internal fun setPref(ctx: Context, key: String, value: Boolean) {
    ctx.getSharedPreferences("rollupvastsurges_prefs", 0).edit().putBoolean(key, value).apply()
    // 同步写入 Settings.Global 供 Xposed hook 进程跨 UID 读取
    try {
        android.provider.Settings.Global.putString(ctx.contentResolver,
            "rollupvastsurges_pref_${key}", if (value) "1" else "0")
    } catch (_: Exception) {}
}

internal fun setPref(ctx: Context, key: String, value: String) {
    ctx.getSharedPreferences("rollupvastsurges_prefs", 0).edit().putString(key, value).apply()
    try {
        android.provider.Settings.Global.putString(ctx.contentResolver,
            "rollupvastsurges_pref_${key}", value)
    } catch (_: Exception) {}
}

// ── App ──
@Composable
fun MDMDisablerApp() {
    val ctx = LocalContext.current
    val info = remember { loadDeviceInfo(ctx) }
    val romType = remember { detectRomType() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedIndex by remember { mutableIntStateOf(0) }

    val navItems = listOf(
        "设备信息" to Icons.Outlined.Info,
        "功能开关" to Icons.Outlined.ToggleOn,
        "Hook 开关" to Icons.Outlined.Lock,
        "美化" to Icons.Outlined.Palette,
        "其他" to Icons.Outlined.Extension,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MiuixTheme.colorScheme.background
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            // ── 左栏：导航菜单 ──
            Surface(
                modifier = Modifier.width(280.dp).fillMaxHeight(),
                color = MiuixTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp).verticalScroll(rememberScrollState())
                ) {
                    // 大标题
                    Text(
                        text = "卷巨澜 :清溪青滩",
                        style = MiuixTheme.textStyles.title2,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    // 导航菜单项
                    navItems.forEachIndexed { i, (label, icon) ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedIndex = i },
                            color = if (selectedIndex == i) MiuixTheme.colorScheme.secondaryContainer
                                    else MiuixTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(icon, contentDescription = label,
                                    tint = if (selectedIndex == i) MiuixTheme.colorScheme.primary
                                           else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                Spacer(Modifier.width(12.dp))
                                Text(label,
                                    color = if (selectedIndex == i) MiuixTheme.colorScheme.primary
                                           else MiuixTheme.colorScheme.onSurface,
                                    fontWeight = if (selectedIndex == i) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            // ── 右栏：内容区 ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MiuixTheme.colorScheme.surfaceVariant)
            ) {
                when (selectedIndex) {
                    0 -> DeviceInfoTab(ctx, info, snackbar, scope)
                    1 -> FuncSwitchTab(ctx, romType, snackbar, scope)
                    2 -> HookSwitchTab(ctx, romType, snackbar, scope)
                    3 -> UiCustomizeTab(ctx, snackbar, scope)
                    4 -> ExtraFeaturesTab(ctx, snackbar, scope)
                }
            }
        }
    }
}

@Composable
fun DeviceInfoTab(ctx: Context, info: DeviceInfo, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    val pm = ctx.packageManager
    val mdmVer = try { pm.getPackageInfo("com.iflytek.ebg.aistudy.mdm", 0).versionName } catch (_: Exception) { null }
    val hwcVer = try { pm.getPackageInfo("com.iflytek.hwc.service", 0).versionName } catch (_: Exception) { null }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(bottom = 24.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {
        // ── Hero 状态卡 ──
        item {
            Card(
                modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 8.dp),
                cornerRadius = 24.dp,
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.primaryContainer,
                    contentColor = MiuixTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "卷巨澜 :清溪青滩",
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "讯飞 AI 学习机 MDM 管控解锁",
                        color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Row {
                        // ROM 类型胶囊
                        Surface(
                            color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(info.romLabel,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        // Android 版本胶囊
                        Surface(
                            color = MiuixTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(info.version,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
                        }
                    }
                }
            }
        }
        item { SectionTitle(text = "系统信息") }
        item {
            SettingsGroup {
                ArrowInfoRow("型号", info.model) { copy(ctx, scope, snackbar, "型号", info.model) }
                ArrowInfoRow("序列号", info.sn) { copy(ctx, scope, snackbar, "序列号", info.sn) }
                ArrowInfoRow("Android ID", info.androidId) { copy(ctx, scope, snackbar, "Android ID", info.androidId) }
                ArrowInfoRow("ROM 版本", info.version)
                ArrowInfoRow("ROM 类型", info.romLabel)
                ArrowInfoRow("LSPosed 作用域", info.scopeHint)
            }
        }
        item { SectionTitle(text = "应用版本") }
        item {
            SettingsGroup {
                ArrowInfoRow("MDM", mdmVer ?: "未安装")
                ArrowInfoRow("HWC Service", hwcVer ?: "未安装")
                ArrowInfoRow("本模块", "v4.0")
            }
        }
        item { SectionTitle(text = "Hook 状态") }
        item {
            SettingsGroup {
                HookStatusSection(ctx)
            }
        }
    }
}

@Composable
fun ArrowInfoRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    BasicComponent(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        title = title,
        summary = value,
    )
}

// ── 双层模型: 功能意图 → Hook 映射 ──
private data class FuncIntent(
    val prefKey: String,
    val label: String,
    val desc: String,
    val defaultOn: Boolean,
    val group: String,
    val icon: ImageVector,
    val controls: List<String>  // 控制的 hook_* / block_* pref key 列表
)

private val funcIntents = listOf(
    FuncIntent("func_app_control", "冻结/解冻应用", "禁止 MDM 冻结和解冻应用", true, "应用管控", Icons.Outlined.Security,
        listOf("hook_mdm_api", "hook_hwc_app_control", "hook_app_control", "hook_forbidden_judge")),
    FuncIntent("func_app_install", "安装黑白名单", "禁止远程限制应用安装", true, "应用管控", Icons.Outlined.Security,
        listOf("hook_mdm_apkinstall", "hook_hwc_app_control")),
    FuncIntent("func_app_background", "应用后台限制", "禁止限制后台运行", true, "应用管控", Icons.Outlined.Security,
        listOf("hook_fw_bgclean", "hook_mdm_kill", "hook_mdm_appactive")),
    FuncIntent("func_network_control", "联网管控", "禁止 MDM 控制应用联网和网址过滤", true, "网络管控", Icons.Outlined.Wifi,
        listOf("hook_mdm_network", "hook_hwc_network_control", "hook_network_control")),
    FuncIntent("func_usb", "USB 禁用", "禁止远程禁用 USB 存储", true, "设备管控", Icons.Outlined.Usb,
        listOf("hook_hwc_usb", "hook_mdm_usb")),
    FuncIntent("func_lockscreen", "锁屏控制", "禁止远程锁定设备", true, "设备管控", Icons.Outlined.Lock,
        listOf("hook_fw_lockscreen")),
    FuncIntent("func_shutdown", "关机/重启", "禁止远程关机、重启", true, "设备管控", Icons.Outlined.Bolt,
        listOf("block_push_shutdown")),
    FuncIntent("func_wipe", "擦除数据", "禁止远程恢复出厂", true, "设备管控", Icons.Outlined.Warning,
        listOf("block_push_device_wipe")),
    FuncIntent("func_push_install", "远程安装 APK", "禁止推送安装应用", true, "推送指令", Icons.Outlined.Wifi,
        listOf("block_push_app_install", "hook_mdm_apkinstall")),
    FuncIntent("func_push_appctrl", "应用冻结指令", "禁止推送冻结/解冻指令", true, "推送指令", Icons.Outlined.Wifi,
        listOf("block_push_app_control")),
    FuncIntent("func_push_screenshot", "截屏指令", "禁止远程截屏", true, "推送指令", Icons.Outlined.Videocam,
        listOf("block_push_screenshot", "hook_mdm_screenshot")),
    FuncIntent("spoof_usage_enabled", "使用时长伪装", "将来源应用的使用时长切片后隐藏到被伪装应用", false, "隐私/反追踪", Icons.Outlined.Sensors,
        listOf("spoof_usage_enabled")),
    FuncIntent("func_stats_report", "使用统计上报", "禁止视频/音频使用时长上报", true, "隐私/反追踪", Icons.Outlined.Sensors,
        listOf("hook_fw_videostats", "hook_fw_audiostats")),
    FuncIntent("func_force_landscape", "强制横屏", "指定应用以横屏启动", false, "系统增强", Icons.Outlined.Palette,
        listOf("hook_fw_landscape", "hook_mdm_landscape")),
    FuncIntent("func_adb_keep", "保持 ADB 开启", "防止 MDM 自动关闭 ADB", false, "系统增强", Icons.Outlined.Devices,
        listOf("hook_mdm_adb")),
)

private val funcByGroup = funcIntents.groupBy { it.group }
private val groupIcons = mapOf(
    "应用管控" to Icons.Outlined.Security,
    "网络管控" to Icons.Outlined.Wifi,
    "设备管控" to Icons.Outlined.Devices,
    "推送指令" to Icons.AutoMirrored.Outlined.Send,
    "隐私/反追踪" to Icons.Outlined.Sensors,
    "系统增强" to Icons.Outlined.Tune,
)

// 反向查找: hook/block pref → 控制它的 FuncIntent (如果被控制)
private fun findControllingFunc(hookKey: String): FuncIntent? =
    funcIntents.firstOrNull { hookKey in it.controls }

// ── Hook 状态概览 — 显示当前启用了哪些 hook ──
private data class HookItem(val prefKey: String, val label: String)
private data class HookGroup(val title: String, val icon: ImageVector, val items: List<HookItem>)

private val hookGroups = listOf(
    HookGroup("系统框架", Icons.Outlined.Security, listOf(
        HookItem("hook_settings_global", "Settings 阻断"),
        HookItem("hook_system_props", "属性阻断"),
        HookItem("hook_force_stop", "forceStop 阻断"),
    )),
    HookGroup("Framework 服务", Icons.Outlined.Settings, listOf(
        HookItem("hook_fw_bgclean", "后台清理"),
        HookItem("hook_fw_lockscreen", "锁屏"),
        HookItem("hook_fw_battery", "电池"),
        HookItem("hook_fw_landscape", "横屏"),
        HookItem("hook_fw_flip", "翻盖"),
        HookItem("hook_fw_videostats", "视频统计"),
        HookItem("hook_fw_audiostats", "音频统计"),
        HookItem("hook_fw_permission", "权限"),
        HookItem("hook_fw_oaid", "OAID"),
    )),
    HookGroup("HWC 服务", Icons.Outlined.Smartphone, listOf(
        HookItem("hook_hwc_app_control", "应用管控"),
        HookItem("hook_hwc_network_control", "联网管控"),
        HookItem("hook_hwc_usb", "USB"),
        HookItem("hook_hwc_display", "显示"),
        HookItem("hook_hwc_audio", "音频"),
        HookItem("hook_hwc_battery", "电池"),
        HookItem("hook_hwc_selfstudy", "自习模式"),
        HookItem("hook_hwc_other", "其他"),
    )),
    HookGroup("MDM 管控", Icons.Outlined.AdsClick, listOf(
        HookItem("hook_mdm_api", "管控 API"),
        HookItem("hook_mdm_applet", "小程序"),
        HookItem("hook_mdm_network", "联网"),
        HookItem("hook_mdm_usb", "USB"),
        HookItem("hook_mdm_landscape", "横屏"),
        HookItem("hook_mdm_applytime", "使用时长"),
        HookItem("hook_mdm_appactive", "使用追踪"),
        HookItem("hook_mdm_apkinstall", "APK 安装"),
        HookItem("hook_mdm_push", "推送"),
        HookItem("hook_mdm_adb", "ADB"),
        HookItem("hook_mdm_switch", "开关监控"),
        HookItem("hook_mdm_boot", "开机启动"),
        HookItem("hook_mdm_v4171_lock", "锁屏/设备管理员"),
        HookItem("hook_mdm_v4171_shutdown", "自动关机"),
        HookItem("hook_mdm_v4171_deviceid", "设备 ID"),
    )),
    HookGroup("推送阻断", Icons.Outlined.Wifi, listOf(
        HookItem("block_push_app_install", "APK 安装"),
        HookItem("block_push_device_lock", "锁定设备"),
        HookItem("block_push_device_wipe", "擦除数据"),
        HookItem("block_push_shutdown", "关机/重启"),
        HookItem("block_push_app_control", "应用冻结"),
        HookItem("block_push_screenshot", "截屏"),
    )),
    HookGroup("安全补漏", Icons.Outlined.Security, listOf(
        HookItem("hook_mdm_provider", "Provider 阻断"),
        HookItem("hook_mdm_kill", "forceStop 阻断"),
        HookItem("hook_mdm_screenshot", "截图"),
        HookItem("hook_mdm_watermark", "水印"),
        HookItem("hook_mdm_nav", "导航栏"),
        HookItem("hook_mdm_v4171_transfer", "AIDL 网关"),
    )),
    HookGroup("TyeLauncher 桌面", Icons.Outlined.Palette, listOf(
        HookItem("hook_tyelauncher_wallpaper", "壁纸跟随系统"),
        HookItem("hook_tyelauncher_workspace", "桌面模式"),
        HookItem("hook_tyelauncher_cards", "卡牌注入"),
        HookItem("hook_tyelauncher_bg", "背景替换"),
        HookItem("hook_tyelauncher_skin", "皮肤切换"),
        HookItem("hook_themecenter", "ThemeCenter 监控"),
        HookItem("hook_icon_pack", "图标包支持"),
    )),
)

@Composable
fun HookStatusSection(ctx: Context) {
    val configPrefs = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)

    // 跨进程 hook 状态读取（三通道）
    fun readHookFileStatus(prefKey: String): Boolean? {
        // 通道C: ContentProvider（优先，最可靠）
        try {
            val bundle = ctx.contentResolver.call(
                com.leaking.rollupvastsurges.hook.HookStatusProvider.CONTENT_URI,
                com.leaking.rollupvastsurges.hook.HookStatusProvider.METHOD_GET, prefKey, null)
            if (bundle != null) {
                val v = bundle.getString("value")
                if (v != null) return when (v) { "1" -> true; "0" -> false; else -> null }
            }
        } catch (_: Exception) {}
        // 通道B: 模块自身 SP（写入方带 hook_status_ 前缀 + Boolean 类型）
        try {
            val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
            val k = "hook_status_${prefKey}"
            if (sp.contains(k)) return sp.getBoolean(k, false)
        } catch (_: Exception) {}
        // 通道A: Settings.Global（跨进程，写入方 key: hook_status_<key>）
        return try {
            val v = android.provider.Settings.Global.getString(ctx.contentResolver,
                "hook_status_${prefKey}")
            when (v) { "1" -> true; "0" -> false; else -> null }
        } catch (_: Exception) { null }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun GroupCard(group: HookGroup) {
        // 实际状态：config ON 且 hook 实际注入成功
        val items = group.items.map { item ->
            val configOn = configPrefs.getBoolean(item.prefKey, true)
            val actualActive = readHookFileStatus(item.prefKey) ?: false
            Triple(item, configOn, actualActive)
        }
        val activeInGroup = items.filter { it.second && it.third }
        val pendingInGroup = items.filter { it.second && !it.third }
        val disabledInGroup = items.filter { !it.second }
        if (activeInGroup.isEmpty() && pendingInGroup.isEmpty()) return

        Card(modifier = Modifier.padding(horizontal = 12.dp)) {
            val summary = buildString {
                if (activeInGroup.isNotEmpty()) append("${activeInGroup.size} 项活跃")
                if (pendingInGroup.isNotEmpty()) {
                    if (isNotEmpty()) append(" · ")
                    append("${pendingInGroup.size} 项等待")
                }
            }
            BasicComponent(
                title = group.title,
                summary = summary.ifEmpty { "已禁用" },
            )
        }
    }

    val activeCount = hookGroups.sumOf { g ->
        g.items.count { configPrefs.getBoolean(it.prefKey, true) && (readHookFileStatus(it.prefKey) ?: false) }
    }
    val hasActualData = hookGroups.any { g ->
        g.items.any { readHookFileStatus(it.prefKey) != null }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = if (hasActualData) "Hook 状态 · $activeCount 项活跃 (运行时)"
            else "Hook 状态 · 等待重启生效")
        if (!hasActualData) {
            Card(modifier = Modifier.padding(horizontal = 12.dp)) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Info, contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("重启后显示实际注入状态",
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        hookGroups.forEach { GroupCard(it) }
    }
}

// ── Functions Tab (功能开关 — 用户想做什么) ──
@Composable
fun FuncSwitchTab(ctx: Context, romType: RomType, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                "功能开关",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        for ((group, items) in funcByGroup) {
            item { SectionTitle(text = group) }
            item {
                SettingsGroup {
                    items.forEach { fi ->
                        FuncSwitchRow(fi)
                    }
                }
            }
        }
    }
}

// ── Hook Switches Tab ──
@Composable
fun HookSwitchTab(ctx: Context, romType: RomType, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { Text("Hook 开关", style = MiuixTheme.textStyles.title2, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) }
        item { SectionTitle(text = "系统框架") }
        item { SettingsGroup { HookSwitchRow("拦截 Settings.Global", "forbidden_package, adb_enabled 等", "hook_settings_global", true)
            HookSwitchRow("拦截 SystemProperties", "persist.vendor.iflytek.*", "hook_system_props", true)
            HookSwitchRow("拦截 forceStopPackage", "阻止 MDM 强制停止应用", "hook_force_stop", false) } }
        item { SectionTitle(text = "ROM 专属 Hook") }
        item { SettingsGroup { when (romType) {
            RomType.A12_STANDARD, RomType.A12_HWC, RomType.A13_STANDARD, RomType.A13_HWC -> {
                LinkedSwitchRow("AppControlService", "应用冻结 / 安装黑白名单", "hook_app_control", true, "hook_hwc_app_control")
                HookSwitchRow("ForbiddenAppJudge", "强制 canLaunch=true 放行", "hook_forbidden_judge", true)
                LinkedSwitchRow("NetworkControl", "拦截 iptables 网址过滤", "hook_network_control", true, "hook_hwc_network_control") }
            RomType.X35G -> { HookSwitchRow("MiaMdmPolicyManager", "拦截 18 个 Binder 方法", "hook_mia_mdm", true)
                HookSwitchRow("TEE 防火墙", "高通 TrustZone 硬件过滤", "hook_tee_firewall", false) }
            else -> { HookSwitchRow("AppControlService", "兜底", "hook_app_control", true)
                HookSwitchRow("NetworkControl", "兜底", "hook_network_control", true) }
        } } }
        item { SectionTitle(text = "Framework 服务") }
        item { SettingsGroup { HookSwitchRow("HwcServiceInternalImpl", "框架 IPC 调度器", "hook_fw_dispatcher", true)
            HookSwitchRow("HwcBgCleanService", "后台进程清理 / 省电", "hook_fw_bgclean", false)
            HookSwitchRow("LockScreenService", "锁屏控制", "hook_fw_lockscreen", true)
            HookSwitchRow("BatteryControlService", "充电控制", "hook_fw_battery", false)
            HookSwitchRow("GlobalLandscapeService", "强制横屏锁定", "hook_fw_landscape", true)
            HookSwitchRow("FlipStructureService", "翻盖皮套检测", "hook_fw_flip", false)
            HookSwitchRow("VideoStatsService", "视频使用统计上报", "hook_fw_videostats", true)
            HookSwitchRow("AudioStatisticsService", "音频使用统计上报", "hook_fw_audiostats", true)
            HookSwitchRow("CustomPermissionService", "自定义权限管理", "hook_fw_permission", false)
            HookSwitchRow("IdentifierService (OAID)", "设备标识符服务", "hook_fw_oaid", false) } }
        item { SectionTitle(text = "HWC 服务") }
        item { SettingsGroup { LinkedSwitchRow("AppControlService", "应用冻结 / 安装黑白名单", "hook_hwc_app_control", true, "hook_app_control")
            LinkedSwitchRow("NetworkControlService", "iptables 网址过滤", "hook_hwc_network_control", true, "hook_network_control")
            HookSwitchRow("UsbForbiddenService", "USB 存储禁用", "hook_hwc_usb", true)
            HookSwitchRow("DisplayService", "亮度 / 刷新率限制", "hook_hwc_display", true)
            HookSwitchRow("AudioControlService", "音量限制", "hook_hwc_audio", true)
            HookSwitchRow("BatteryTemperatureAlert", "电池温度告警", "hook_hwc_battery", true)
            HookSwitchRow("SelfStudyModeService", "自习模式控制", "hook_hwc_selfstudy", false)
            HookSwitchRow("其他 HWC 服务", "Log / Update / PowerUI", "hook_hwc_other", false) } }
        item { SectionTitle(text = "MDM 主程序") }
        item { SettingsGroup { HookSwitchRow("管控 API", "XXJForbidAPI / X35GForbidAPI 全部方法", "hook_mdm_api", true)
            HookSwitchRow("小程序管控", "AppletControlAPI", "hook_mdm_applet", true)
            HookSwitchRow("网络管控", "NetworkControl / UrlFilter", "hook_mdm_network", true)
            HookSwitchRow("USB 管控", "UsbForbiddenService", "hook_mdm_usb", true)
            HookSwitchRow("横屏管控", "AppActivityLandscapeConfig", "hook_mdm_landscape", true)
            HookSwitchRow("使用时长管控", "ApplyTimeReceiver / X2ApplyTime", "hook_mdm_applytime", true)
            HookSwitchRow("应用使用追踪", "AppActive 上报", "hook_mdm_appactive", true)
            HookSwitchRow("远程安装 APK", "apk_install 推送安装", "hook_mdm_apkinstall", true)
            HookSwitchRow("推送消息接收", "AIStudyPushMessageReceiver", "hook_mdm_push", true)
            HookSwitchRow("ADB 监控", "ADBObserver 自动回写", "hook_mdm_adb", true)
            HookSwitchRow("开关监控", "KillSwitchObserver + ForbiddenAppReceiver", "hook_mdm_switch", true)
            HookSwitchRow("开机广播", "BootReceiver 自启管控", "hook_mdm_boot", false)
            HookSwitchRow("安全补漏: Provider", "BusinessControlContentProvider", "hook_mdm_provider", true)
            HookSwitchRow("安全补漏: forceStop", "ForceStopHelper/KillProcess 等", "hook_mdm_kill", true)
            HookSwitchRow("安全补漏: 截图", "FastBlur/ScreenShot 等", "hook_mdm_screenshot", true)
            HookSwitchRow("安全补漏: 非商店水印", "InstalledNonStoreManager", "hook_mdm_watermark", true)
            HookSwitchRow("安全补漏: 导航栏", "GlobalGestureAPI setZenMode", "hook_mdm_nav", true) } }
        item { SectionTitle(text = "其他目标") }
        item { SettingsGroup { HookSwitchRow("SystemUI", "解锁状态栏限制", "hook_systemui", false,
            enableWarn = "此功能会 hook SystemUI 中所有 disable 相关方法，可能导致状态栏、导航手势、音量控制等系统界面异常。\n\nMDM 通常通过 zen_mode 设置控制界面限制，已被其他 hook 拦截，此功能一般不需要开启。\n\n确定要开启吗？") } }
        item { SectionTitle(text = "远程推送指令拦截") }
        item { SettingsGroup { HookSwitchRow("拦截远程安装 APK", "app_install 指令", "block_push_app_install", true)
            HookSwitchRow("拦截锁定设备", "device_lock 指令", "block_push_device_lock", true)
            HookSwitchRow("拦截擦除数据", "device_wipe 指令", "block_push_device_wipe", true)
            HookSwitchRow("拦截远程关机/重启", "shutdown / reboot 指令", "block_push_shutdown", true)
            HookSwitchRow("拦截应用冻结指令", "app_control 指令", "block_push_app_control", true)
            HookSwitchRow("拦截截屏指令", "device_screenshot_control", "block_push_screenshot", true) } }
    }
}

// ── Extra Features Tab ──
@Composable
fun ExtraFeaturesTab(ctx: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    var hasError by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            // 预加载检查 — 若这里抛异常则显示错误兜底 UI
            ctx.packageName
        } catch (e: Throwable) {
            hasError = true
            errorMsg = "${e::class.simpleName}: ${e.message}"
        }
    }

    if (hasError) {
        Column(
            modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("内容加载异常",
                color = MiuixTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(errorMsg,
                color = MiuixTheme.colorScheme.onSurfaceSecondary)
        }
    } else {
        ExtraFeaturesTabContent(ctx, snackbar, scope)
    }
}

@Composable
private fun ExtraFeaturesTabContent(ctx: Context, snackbar: SnackbarHostState, scope: kotlinx.coroutines.CoroutineScope) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Text(
                "其他",
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
        item { SectionTitle(text = "强制横屏") }
        item {
            SettingsGroup {
                SwitchRow("启用强制横屏", "选中应用以横屏启动", "force_landscape_enabled", false)
                LandscapeTargetSelector()
            }
        }
        item { SectionTitle(text = "使用时长伪装") }
        item {
            SettingsGroup {
                SwitchRow("启用伪装", "将来源应用的使用时长切片后隐藏到被伪装应用", "spoof_usage_enabled", false)
                SpoofTargetSelector()
                SpoofSourceSelector()
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        // 应用列表上报隐藏
        item { SectionTitle(text = "应用列表上报隐藏") }
        item {
            SettingsGroup {
                AppListHiddenSelector(ctx)
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { SectionTitle(text = "笔按键重映射") }
        item {
            SettingsGroup {
                PenButtonActionSelector()
                NativeStatusRow()
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { SectionTitle(text = "安全保护") }
        item {
            SettingsGroup {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        ctx.getSharedPreferences("mdm_crash_record", 0).edit().clear().apply()
                        scope.launch { snackbar.showSnackbar("崩溃计数已重置") }
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("重置崩溃计数")
                        Text("应用 crash 3 次后自动暂停 hook",
                            color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                    Text("重置", color = MiuixTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item { SectionTitle(text = "关于") }
        item {
            Card(
                cornerRadius = 16.dp,
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("卷巨澜 :清溪青滩",
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text("Powered by leaking software next project",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("基于 Miuix 设计",
                        color = MiuixTheme.colorScheme.onSurfaceSecondary)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
// SectionTitle → MIUI HyperOS 风格分组标题（灰色小字，无竖线）
@Composable
fun SectionTitle(text: String, icon: @Composable (() -> Unit)? = null) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.subtitle,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 6.dp)
    )
}

// SettingsGroup → MIUI Card (圆角 + 背景区分)
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Column(content = content)
    }
}
@Composable
fun LinkedSwitchRow(title: String, desc: String, prefKey: String, defaultVal: Boolean, linkedKey: String?) {
    val ctx = LocalContext.current
    var checked by remember { mutableStateOf(prefBool(ctx, prefKey, defaultVal)) }
    val linkedVisible = linkedKey != null && linkedKey.isNotEmpty() && linkedKey != prefKey
    val summary = if (linkedVisible) "$desc (联动)" else desc

    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = {
            checked = it
            setPref(ctx, prefKey, it)
            if (linkedVisible) setPref(ctx, linkedKey, it)
        }
    )
}

@Composable
fun SwitchRow(title: String, desc: String, prefKey: String, defaultVal: Boolean) {
    val ctx = LocalContext.current
    var checked by remember { mutableStateOf(prefBool(ctx, prefKey, defaultVal)) }

    SwitchPreference(
        title = title,
        summary = desc,
        checked = checked,
        onCheckedChange = {
            checked = it
            setPref(ctx, prefKey, it)
        }
    )
}

@Composable
fun NativeStatusRow() {
    var nativeStatus by remember { mutableStateOf<String?>(null) }

    // 首帧后异步加载，避免首帧合成时反射异常导致整个 tab 崩溃
    LaunchedEffect(Unit) {
        nativeStatus = try {
            getNativePenhookStatus()
        } catch (_: Exception) { "unknown" }
    }

    val (color, label) = when (nativeStatus) {
        null -> Color(0xFF9E9E9E) to "加载中..."
        "active" -> Color(0xFF4CAF50) to "Native 注入运行中"
        "blocked" -> Color(0xFFFF9800) to "Native 注入已禁用（崩溃保护）"
        "unavailable" -> Color(0xFF9E9E9E) to "Native 库未部署"
        else -> Color(0xFFFF5722) to "状态未知"
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, shape = CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(label,
            color = MiuixTheme.colorScheme.onSurface)
        Spacer(Modifier.weight(1f))
        if (nativeStatus == "blocked") {
            Text("恢复", color = MiuixTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable {
                    // 清除两种路径下的 crash 计数
                    try { java.io.File("/data/system/penhook/crash_count").writeText("0") } catch (_: Exception) {}
                    try { java.io.File("/data/system/penhook/blocked").delete() } catch (_: Exception) {}
                    try { java.io.File("/data/system/penhook/blocked").delete() } catch (_: Exception) {}
                    try { java.io.File("/data/system/penhook/status").writeText("unavailable") } catch (_: Exception) {}
                }
            )
        }
    }
}

// ── 笔按键动作下拉选择器 ──
@Composable
private fun PenButtonActionSelector() {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    val actions = listOf(
        "none" to "无",
        "eraser" to "笔刷切换",
        "mouse" to "鼠标左键",
        "menu" to "菜单",
        "back" to "返回",
        "custom" to "自定义 KeyCode"
    )
    var selected by remember { mutableStateOf(prefs.getString("pen_button_action", "none") ?: "none") }
    val selectedIndex = actions.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val items = actions.map { it.second }

    OverlayDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = "笔按键功能",
        summary = "侧键按下触发的操作",
        onSelectedIndexChange = { idx: Int ->
            val key = actions[idx].first
            selected = key
            setPref(ctx, "pen_button_action", key)
        }
    )
    if (selected == "custom") {
        TextField(
            value = prefs.getString("pen_button_custom_key", "82") ?: "82",
            onValueChange = { v ->
                if (v.all { it.isDigit() } && v.length <= 5) {
                    prefs.edit().putString("pen_button_custom_key", v).apply()
                }
            },
            label = "KeyCode (数字)",
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Text("例: 82=菜单 4=返回 3=Home 187=最近任务",
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(top = 4.dp))
    }
}

// ── 功能意图开关 (双层模型: 通断管理所有关联 hook) ──
@Composable
private fun FuncSwitchRow(fi: FuncIntent) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    var checked by remember { mutableStateOf(prefBool(ctx, fi.prefKey, fi.defaultOn)) }
    val anyHookOff = fi.controls.any { !prefs.getBoolean(it, true) }
    val isRestricted = checked && anyHookOff
    val summary = if (isRestricted) "${fi.desc} (部分拦截)" else fi.desc

    SwitchPreference(
        title = fi.label,
        summary = summary,
        checked = checked,
        onCheckedChange = { on: Boolean ->
            checked = on
            setPref(ctx, fi.prefKey, on)
            for (hk in fi.controls) {
                setPref(ctx, hk, on)
            }
        }
    )
}

// ── Hook 开关 (HookSwitchTab 专用，受功能意图管理) ──
@Composable
fun HookSwitchRow(title: String, desc: String, prefKey: String, defaultVal: Boolean,
                   enableWarn: String? = null) {
    val ctx = LocalContext.current
    var checked by remember { mutableStateOf(prefBool(ctx, prefKey, defaultVal)) }
    val controllingFunc = findControllingFunc(prefKey)
    val funcLabel = controllingFunc?.label
    val showDialog = remember { mutableStateOf(false) }
    val showEnableDialog = remember { mutableStateOf(false) }

    OverlayDialog(
        show = showDialog.value,
        title = "关闭 Hook 途径",
        summary = "关闭「$title」会影响「$funcLabel」的拦截效果，确定继续吗？",
        onDismissRequest = { showDialog.value = false },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(text = "保留", onClick = { showDialog.value = false })
            TextButton(text = "关闭", onClick = {
                checked = false
                setPref(ctx, prefKey, false)
                showDialog.value = false
            })
        }
    }

    OverlayDialog(
        show = showEnableDialog.value,
        title = "风险功能",
        summary = enableWarn,
        onDismissRequest = { showEnableDialog.value = false },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(text = "取消", onClick = { showEnableDialog.value = false })
            TextButton(text = "仍然开启", onClick = {
                checked = true
                setPref(ctx, prefKey, true)
                showEnableDialog.value = false
            })
        }
    }

    val summary = if (controllingFunc != null) "$desc (由「$funcLabel」管理)" else desc

    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = { on: Boolean ->
            if (on) {
                if (enableWarn != null) {
                    showEnableDialog.value = true
                } else {
                    checked = true
                    setPref(ctx, prefKey, true)
                }
            } else if (controllingFunc != null) {
                showDialog.value = true
            } else {
                checked = false
                setPref(ctx, prefKey, false)
            }
        }
    )
}

@Composable
fun AppPickerDialog(
    show: MutableState<Boolean>,
    selectedPkg: MutableState<String>,
    prefsKey: String,
    title: String,
    multiSelect: Boolean = false
) {
    val ctx = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val prefs = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)

    if (!show.value) return

    val pm = ctx.packageManager
    var installed by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    // 包列表扫描放 IO 线程，避免 getInstalledApplications + 逐包 getApplicationLabel
    // （跨 binder 调用）阻塞主线程导致 ANR
    LaunchedEffect(show.value) {
        if (!show.value) return@LaunchedEffect
        loading = true
        installed = withContext(Dispatchers.IO) {
            try {
                pm.getInstalledApplications(0)
                    .filter { it.enabled }
                    .map { AppInfo(pm.getApplicationLabel(it).toString(), it.packageName) }
                    .sortedBy { it.label }
            } catch (_: Exception) { emptyList() }
        }
        loading = false
    }
    val filtered = remember(searchQuery, installed) {
        if (searchQuery.isEmpty()) installed
        else installed.filter { it.label.contains(searchQuery, true) || it.pkg.contains(searchQuery, true) }
    }

    val initialSet = if (multiSelect)
        selectedPkg.value.split(",").filter { it.isNotEmpty() }.toMutableSet()
    else if (selectedPkg.value.isNotEmpty()) mutableSetOf(selectedPkg.value)
    else mutableSetOf()
    val selectedSet = remember(selectedPkg.value) { mutableStateOf(initialSet) }

    OverlayDialog(
        show = show.value,
        title = if (multiSelect) "$title（多选）" else title,
        onDismissRequest = { show.value = false; searchQuery = "" },
    ) {
        Column {
            TextField(value = searchQuery, onValueChange = { searchQuery = it },
                label = "搜索应用...", singleLine = true,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                if (loading) {
                    item {
                        Text("加载应用列表...",
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            color = MiuixTheme.colorScheme.onSurfaceSecondary)
                    }
                }
                items(filtered) { app ->
                    val isChecked = app.pkg in selectedSet.value
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                if (multiSelect) {
                                    val s = selectedSet.value.toMutableSet()
                                    if (isChecked) s.remove(app.pkg) else s.add(app.pkg)
                                    selectedSet.value = s
                                } else {
                                    selectedPkg.value = app.pkg
                                    prefs.edit().putString(prefsKey, app.pkg).apply()
                                    show.value = false; searchQuery = ""
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(app.label)
                            Text(app.pkg,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary)
                        }
                        if (multiSelect) {
                            Checkbox(state = if (isChecked) ToggleableState.On else ToggleableState.Off, onClick = null)
                        } else if (isChecked) {
                            Text("[OK]", color = MiuixTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (multiSelect) {
                Spacer(Modifier.height(8.dp))
                Text("已选 ${selectedSet.value.size} 个应用",
                    color = MiuixTheme.colorScheme.onSurfaceSecondary)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(text = "取消", onClick = { show.value = false; searchQuery = "" })
                if (multiSelect) {
                    TextButton(text = "确定 (${selectedSet.value.size})", onClick = {
                        selectedPkg.value = selectedSet.value.joinToString(",")
                        prefs.edit().putString(prefsKey, selectedPkg.value).apply()
                        show.value = false; searchQuery = ""
                    })
                }
            }
        }
    }
}

@Composable
fun PkgSelectorRow(
    title: String,
    prefsKey: String,
    placeholder: String,
    dialogTitle: String
) {
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    val selectedPkg = remember { mutableStateOf(prefs.getString(prefsKey, "") ?: "") }
    val showDialog = remember { mutableStateOf(false) }
    val pkg = selectedPkg.value
    val pkgs = pkg.split(",").filter { it.isNotEmpty() }
    val localPm = ctx.packageManager

    val label = if (pkgs.isEmpty()) "未选择"
    else if (pkgs.size == 1) {
        try { localPm.getApplicationLabel(localPm.getApplicationInfo(pkgs[0], 0)).toString() }
        catch (_: Exception) { pkgs[0] }
    } else "已选 ${pkgs.size} 个应用"

    val sub = if (pkg.isNotEmpty()) pkg else placeholder

    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { showDialog.value = true }
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title,
                color = MiuixTheme.colorScheme.onSurface)
            Text(sub,
                color = if (pkg.isNotEmpty()) MiuixTheme.colorScheme.onSurfaceSecondary
                        else MiuixTheme.colorScheme.error)
        }
        Text(label,
            fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.primary)
    }
    AppPickerDialog(showDialog, selectedPkg, prefsKey, dialogTitle, multiSelect = true)
}

@Composable
fun SpoofTargetSelector() {
    PkgSelectorRow("被伪装应用（收时间）", "spoof_target_pkg",
        "你想让家长看到的应用（收时间）", "选择被伪装应用")
}

@Composable
fun SpoofSourceSelector() {
    PkgSelectorRow("来源应用（出时间）", "spoof_source_pkg",
        "你不想让家长看到的应用（出时间）", "选择来源应用")
}

@Composable
fun LandscapeTargetSelector() {
    PkgSelectorRow("横屏应用", "force_landscape_pkg",
        "点击选择要强制横屏的应用", "选择横屏应用")
}

private data class AppInfo(val label: String, val pkg: String)

private fun copy(ctx: Context, scope: kotlinx.coroutines.CoroutineScope, snackbar: SnackbarHostState, label: String, value: String) {
    val clip = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clip.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
    scope.launch { snackbar.showSnackbar("已复制 $label") }
}

// 图标包选择器已移至 ThemeCardActivity 的 CardManagerUI 中

// ── 应用列表上报隐藏选择器 ──
@Composable
fun AppListHiddenSelector(ctx: Context) {
    val prefsKey = "hidden_app_list"
    PkgSelectorRow("隐藏指定应用不上报", prefsKey,
        "选择要隐藏的应用（不上报给家长端）", "隐藏应用")
}

// ── 桌面布局选择器 ──
@Composable
fun LauncherLayoutSelector(ctx: Context) {
    val sp = ctx.getSharedPreferences("rollupvastsurges_prefs", 0)
    val layouts = listOf(
        "" to "自动 (设备默认)",
        "activity_aistudy_launcher_ctg" to "默认通用",
        "activity_aistudy_launcher_ctg_ca30pro" to "CA30Pro",
        "activity_aistudy_launcher_ctg_jzxos" to "极战线OS",
        "activity_aistudy_launcher_ctg_me30" to "ME30",
        "activity_aistudy_launcher_ctg_new_launcher" to "新版桌面",
        "activity_aistudy_launcher_ctg_new_launcher_su30pro" to "SU30Pro",
        "activity_aistudy_launcher_ctg_sa30pro" to "SA30Pro",
        "activity_aistudy_launcher_p30_5g" to "P30_5G",
        "activity_aistudy_launcher_mini" to "Q10 Mini",
    )
    var selected by remember { mutableStateOf(sp.getString("layout_override", "") ?: "") }
    val selectedIndex = layouts.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val items = layouts.map { it.second }

    OverlayDropdownPreference(
        items = items,
        selectedIndex = selectedIndex,
        title = "桌面布局",
        onSelectedIndexChange = { idx: Int ->
            selected = layouts[idx].first
            sp.edit().putString("layout_override", selected).apply()
        }
    )
}




