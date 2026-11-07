package com.leaking.rollupvastsurges.resolver

import android.content.pm.PackageInfo
import com.leaking.rollupvastsurges.compat.MethodHookParam
import com.leaking.rollupvastsurges.compat.XposedBridge
import com.leaking.rollupvastsurges.compat.XposedHelpers
import com.leaking.rollupvastsurges.hook.CustomControlCard
import com.leaking.rollupvastsurges.hook.HookEngine
import com.leaking.rollupvastsurges.hook.IconPackManager
import com.leaking.rollupvastsurges.hook.InterfaceProxy
import com.leaking.rollupvastsurges.hook.LkCardManager
import com.leaking.rollupvastsurges.hook.LkWebViewCard
import java.lang.reflect.Method

/**
 * provider 运行上下文 —— 规则表带下来的数据。
 *
 * 关键: [values] 让 provider 不必硬编码类名/映射表。
 * 例如 push_block 需要判断参数是否为某消息类, 该类名由规则表给出,
 * Java 侧只写比较逻辑 —— 逆向 classes.dex 看不到目标。
 */
class ActionContext(
    val ruleId: String,
    val values: List<String>,
    val method: Method,
    val log: (String) -> Unit,
    /** 目标进程的 ClassLoader(给需要加载目标类/反射的 provider)。 */
    val classLoader: ClassLoader = method.declaringClass.classLoader,
)

/**
 * ── C 类 hook 的落地方式 ──
 *
 * C 类(after-hook 变换结果 / 运行时计算)的**逻辑**必须留在 Java(要调 View/Bitmap/
 * PackageManager 等 Android API), 但**目标**(类名/方法名/条件/映射表)全部来自
 * native 规则表。provider 收到的 [MethodHookParam] 是完整的, 因此能读原结果、
 * 改 args、碰 thisObject、带状态 —— 结构上可承载任意 C 类逻辑。
 *
 * 注册表在 object 初始化时填充, 由 HookInstaller 按名字查找。
 */
object ActionProviders {
    private val map = HashMap<String, (MethodHookParam, ActionContext) -> Unit>()

    fun get(name: String): ((MethodHookParam, ActionContext) -> Unit)? = map[name]

    private fun reg(name: String, f: (MethodHookParam, ActionContext) -> Unit) { map[name] = f }

    init {
        // 截屏阻断: View.setWindowVisibility(VISIBLE) 时给所在 Activity 加 FLAG_SECURE (before)
        reg("screenshot_secure") { p, ctx ->
            try {
                val pref = ctx.values.getOrNull(0) ?: return@reg
                if (!HookEngine.readBool(pref, true)) return@reg
                if ((p.args?.getOrNull(0) as? Int) != android.view.View.VISIBLE) return@reg
                val v = p.thisObject as? android.view.View ?: return@reg
                val act = v.context as? android.app.Activity ?: return@reg
                act.window.setFlags(8192, 8192)
            } catch (_: Throwable) {}
        }

        // PackageManager 禁用拦截: state==DISABLED 且 pkg 在 TARGET 名单(args) -> 拦 (before)
        reg("pm_block_disable") { p, ctx ->
            try {
                val a = p.args ?: return@reg
                if ((a.getOrNull(1) as? Int) != android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED) return@reg
                val pkg = a.getOrNull(0) as? String ?: return@reg
                if (ctx.values.contains(pkg)) p.result = null
            } catch (_: Throwable) {}
        }

        // 使用统计伪装: after 返回空表 (args: [prefKey])
        reg("spoof_usage_stats") { p, ctx ->
            try {
                val pref = ctx.values.getOrNull(0) ?: return@reg
                if (HookEngine.readBool(pref, false)) p.result = ArrayList<Any>()
            } catch (_: Throwable) {}
        }

        // 强制横屏: after 设方向 (args: [prefKey]); 安装期已按 pref 门控
        reg("force_landscape") { p, ctx ->
            try {
                val pref = ctx.values.getOrNull(0) ?: return@reg
                if (!HookEngine.readBool(pref, false)) return@reg
                (p.thisObject as? android.app.Activity)?.requestedOrientation =
                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } catch (_: Throwable) {}
        }

        // AssetManager 资源替换: pref "asset_override_<path>" -> 外部文件 (before)
        // 命中 negative cache 的路径直接跳过, 避免热路径反复读 pref。
        reg("asset_override") { p, _ ->
            try {
                val path = p.args?.getOrNull(0) as? String ?: return@reg
                if (assetMiss.contains(path)) return@reg
                val f = HookEngine.readStr("asset_override_" + path, "")
                if (f.isEmpty()) { assetMiss.add(path); return@reg }
                val file = java.io.File(f)
                if (file.exists() && file.isFile) p.result = java.io.FileInputStream(file)
                else assetMiss.add(path)
            } catch (_: Throwable) {}
        }
        // ═══════════ StudyPadFreedom 派生: 壁纸/横屏 ═══════════

        // SystemUI 通知栏媒体背景 -> 系统壁纸 (before)
        reg("sysui_notif_wallpaper") { p, _ ->
            try {
                val bmp = readSystemWallpaperBitmap() ?: return@reg
                val o = p.thisObject ?: return@reg
                setFieldOrNull(o, "mStaticWallpaperBitmap", bmp)
                setFieldOrNull(o, "mUseStaticWallpaper", true)
            } catch (_: Throwable) {}
        }

        // 锁屏下拉背景 -> 系统壁纸 (after)
        reg("keyguard_wallpaper") { p, _ ->
            try {
                val o = p.thisObject ?: return@reg
                val iv = getFieldOrNull(o, "mKeyguardWallpaperView") as? android.widget.ImageView ?: return@reg
                val bmp = readSystemWallpaperBitmap() ?: return@reg
                iv.setImageBitmap(bmp)
            } catch (_: Throwable) {}
        }

        // 主题壁纸加载路径 -> 系统壁纸文件 (before)
        reg("keyguard_theme_path") { p, _ ->
            try {
                val o = p.thisObject ?: return@reg
                setFieldOrNull(o, "mSysUiStaticWallpaperPath", "/data/system/users/0/wallpaper")
            } catch (_: Throwable) {}
        }

        // Recents 壁纸 zoom 置 0 (before)
        reg("wallpaper_zoom") { p, _ ->
            try { if (p.args != null && p.args.size > 1) p.args[1] = 0.0f } catch (_: Throwable) {}
        }

        // RecentsDragLayer 背景绕过: 类名命中才拦, 匹配串来自规则 args (before)
        reg("recents_draglayer") { p, ctx ->
            try {
                val pat = ctx.values.getOrNull(0) ?: return@reg
                val n = p.thisObject?.javaClass?.name ?: return@reg
                if (n.contains(pat)) p.result = null
            } catch (_: Throwable) {}
        }

        // 桌面主题背景层隐藏: 类名完全命中则 GONE (after=attach 设 / before=setVisibility 改参)
        reg("theme_bg_gone") { p, ctx ->
            try {
                val want = ctx.values.getOrNull(0) ?: return@reg
                val obj = p.thisObject ?: return@reg
                if (obj.javaClass.name != want) return@reg
                val v = obj as? android.view.View ?: return@reg
                if (ctx.method.name == "onAttachedToWindow") {
                    v.visibility = android.view.View.GONE
                } else if (p.args != null && p.args.isNotEmpty()) {
                    if ((p.args[0] as? Int) != android.view.View.GONE) p.args[0] = android.view.View.GONE
                }
            } catch (_: Throwable) {}
        }

        // 锁屏壁纸文件: 动态壁纸禁用, 静态换成系统壁纸句柄 (before)
        reg("lock_wallpaper") { p, _ ->
            try {
                if (p.args == null || p.args.size < 2) return@reg
                val fileName = p.args[1] as? String ?: return@reg
                if (fileName == "live_wallpaper.mp4") { p.result = null; return@reg }
                val c = p.args[0] as? android.content.Context ?: return@reg
                val wm = android.app.WallpaperManager.getInstance(c)
                val pfd = wm.javaClass.getMethod("getWallpaperFile", Int::class.javaPrimitiveType)
                    .invoke(wm, 1) as? android.os.ParcelFileDescriptor ?: return@reg
                p.result = android.content.res.AssetFileDescriptor(pfd, 0, -1)
            } catch (_: Throwable) {}
        }

        // 强制横屏: 用户名单注入 GlobalLandscapeManager.userLandAppConfig 静态表 (after)
        // args: [0]=GlobalLandscapeAppConfig 类名, [1]=名单 prefKey(逗号分隔包名)
        reg("landscape_force") { p, ctx ->
            try {
                val cfgName = ctx.values.getOrNull(0) ?: return@reg
                val prefKey = ctx.values.getOrNull(1) ?: return@reg
                val now = android.os.SystemClock.elapsedRealtime()
                if (landscapePrefsMs == 0L || now - landscapePrefsMs > 30000L) {
                    landscapePrefsMs = now
                    landscapeCfgSet = HookEngine.readStr(prefKey, "").split(",")
                        .map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                }
                val mgrCls = ctx.method.declaringClass
                if (landscapeCfgSet != landscapeAppliedSet) {
                    try {
                        val f = mgrCls.getDeclaredField("mNeedUpdateAllConfig")
                        f.isAccessible = true
                        f.setBoolean(null, true)
                    } catch (_: Throwable) {}
                    val m0 = landscapeMap(mgrCls)
                    if (m0 != null) for (old in landscapeAppliedSet) if (old !in landscapeCfgSet) m0.remove(old)
                    landscapeAppliedSet = landscapeCfgSet
                }
                if (landscapeCfgSet.isEmpty()) return@reg
                val m = landscapeMap(mgrCls) ?: return@reg
                val cfgCls = Class.forName(cfgName, false, ctx.classLoader)
                for (pk in landscapeCfgSet) if (!m.containsKey(pk)) m[pk] = cfgCls.getDeclaredConstructor().newInstance()
            } catch (_: Throwable) {}
        }
        // ── v4171: 设备 ID (after) — legacy 仅记日志, 不改结果 ──
        // 规则表: ...mdm.deviceid.AppDeviceIdGenerator, 方法前缀 a*, 0参且返回String
        reg("deviceid_spoof") { p, ctx ->
            val m = ctx.method
            if (m.parameterTypes.isNotEmpty() || m.returnType != String::class.java) return@reg
            ctx.log("[MDM] AppDeviceId spoofed")
        }

        // ── HWC 服务管控 (before) — 前缀在规则表, 这里按参数形状自适应阻断 ──
        // legacy: 方法名以 a 开头 && 参数非空 && 含 boolean/int 参数 -> 按返回类型给默认值
        reg("hwc_svc_block") { p, ctx ->
            val m = ctx.method
            val pts = m.parameterTypes
            if (pts.isEmpty()) return@reg
            val hit = pts.any { it == Boolean::class.javaPrimitiveType || it == Int::class.javaPrimitiveType }
            if (!hit) return@reg
            ctx.log("[MDM][HWC] Blocked " + m.declaringClass.name + "." + m.name)
            when (m.returnType) {
                Boolean::class.javaPrimitiveType -> p.result = false
                Int::class.javaPrimitiveType -> p.result = 0
                Long::class.javaPrimitiveType -> p.result = 0L
                String::class.java -> p.result = ""
                Void.TYPE -> p.result = null
                else -> Unit
            }
        }

        // ── ThemeCenter: 主题切换监控 (before, 仅记日志) ──
        reg("theme_switch_log") { p, ctx ->
            if (p.args == null || p.args.isEmpty()) return@reg
            val themeId = p.args[0] as? String ?: return@reg
            ctx.log("[MDM][ThemeCenter] Theme switch: " + themeId)
        }

        // ── ThemeCenter: 一致性检测绕过 (after, true->false) ──
        reg("theme_consistency") { p, ctx ->
            if (ctx.method.parameterTypes.isNotEmpty()) return@reg
            if (p.result == true) p.result = false
        }

        // ── ThemeCenter: ThemeHomeActivity 重定向到模块 ThemeCardActivity (before) ──
        reg("theme_redirect") { p, ctx ->
            val activity = p.thisObject as? android.app.Activity ?: return@reg
            try {
                val intent = android.content.Intent(activity, com.leaking.rollupvastsurges.ui.ThemeCardActivity::class.java)
                activity.startActivity(intent)
                activity.finish()
                ctx.log("[MDM][ThemeCenter] Redirected to ThemeCardActivity")
            } catch (_: Throwable) {}
        }
        // ── TopPackage 来源伪装 (after) ──
        // 规则表目标: com.iflytek.cbg.common.screenshot.TopPackageUtils
        //             getTopPackageName / getTopActivity
        reg("spoof_pkg") { p, _ ->
            val src = prefs("spoof_source_pkg")
            if (!HookEngine.readBool("spoof_usage_enabled", false) || src.isEmpty()) return@reg
            val r = p.result as? String ?: return@reg
            if (r in src) p.result = "com.android.launcher"
        }

        // ── 使用时长伪装 (before) ──
        // 规则表目标: ...mdm.usage.usetime.appuse.AppUseTimeGetter.a(Long,Long)->Long
        reg("app_use_time_spoof") { p, _ ->
            val t = HookEngine.readStr("spoof_use_time", "21600000").toLongOrNull() ?: 21600000L
            p.result = t
            if (p.args != null && p.args.size >= 2) {
                p.args[0] = 0L
                p.args[1] = 0L
            }
        }

        // ── 推送指令拦截 (before) ──
        // 规则表目标: ...mdm.AIStudyPushMessageReceiverImpl  handleMessage / onReceive
        // values[0]     = 推送消息类名(由规则表给出, Java 不硬编码)
        // values[1..n]  = "cmd=prefKey" 映射(同样来自规则表)
        reg("push_block") { p, ctx ->
            val msgCls = ctx.values.firstOrNull() ?: return@reg
            val cmdMap = HashMap<String, String>()
            for (kv in ctx.values.drop(1)) {
                val i = kv.indexOf('=')
                if (i > 0) cmdMap[kv.substring(0, i)] = kv.substring(i + 1)
            }
            val args = p.args ?: return@reg
            for (arg in args) {
                if (arg == null || arg.javaClass.name != msgCls) continue
                val cmd = try {
                    (arg.javaClass.getMethod("getType").invoke(arg) as? String)
                        ?: (arg.javaClass.getMethod("getCommand").invoke(arg) as? String)
                } catch (_: Throwable) { null } ?: continue
                val key = cmdMap[cmd] ?: continue
                if (HookEngine.readBool(key, true)) { p.result = null; break }
            }
        }

        // ── 应用列表上报过滤 (after) ──
        // 规则表目标: com.iflytek.cbg.common.utils.PackageInfoUtils
        reg("applist_filter") { p, _ ->
            val original = p.result as? List<*> ?: return@reg
            val hidden = hiddenSet() ?: return@reg
            p.result = original.filter { (it as? PackageInfo)?.packageName !in hidden }
        }
        reg("applist_hide_one") { p, _ ->
            val pkg = p.args?.getOrNull(1) as? String ?: return@reg
            val hidden = hiddenSet() ?: return@reg
            if (pkg in hidden) p.result = null
        }

        // ── 最近任务背景图 (after) ──
        // 规则表目标: com.android.quickstep.views.RecentsView.onAttachedToWindow
        reg("recents_bg") { p, _ ->
            val view = p.thisObject as? android.view.View ?: return@reg
            if (injectedViews.containsKey(view)) return@reg
            val bg = loadRecentsBg() ?: return@reg
            view.setBackground(bg)
            injectedViews[view] = true
            XposedBridge.log("[MDM] Recents background applied")
        }

        // ═══════════ SystemUIHook (com.android.systemui) ═══════════

        // QS 磁贴列数: 写父类字段 mResourceColumns
        reg("qs_tile_columns") { p, _ ->
            try {
                val view = p.thisObject as? android.view.View ?: return@reg
                val isLand = view.resources.configuration.orientation ==
                    android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val cols = HookEngine.readInt(if (isLand) "qs_tile_columns_landscape" else "qs_tile_columns_portrait", 0)
                if (cols <= 0) return@reg
                XposedHelpers.setIntField(view, "mResourceColumns", cols)
            } catch (_: Throwable) {}
        }

        // QS 面板圆角: args[2] = px
        reg("qs_panel_corner") { p, _ ->
            try {
                val radiusDp = HookEngine.readInt("qs_panel_corner_radius", 0)
                val view = p.thisObject as? android.view.View ?: return@reg
                if (radiusDp <= 0) return@reg
                val px = (radiusDp * view.resources.displayMetrics.density).toInt()
                p.args[2] = px
            } catch (_: Throwable) {}
        }

        // QS 隐藏磁贴文字: labelContainer 隐藏
        reg("qs_hide_label") { p, _ ->
            try {
                if (HookEngine.readBool("qs_tile_show_label", true)) return@reg
                val view = p.thisObject ?: return@reg
                val f = view::class.java.getDeclaredField("labelContainer")
                f.isAccessible = true
                (f.get(view) as? android.view.View)?.visibility = android.view.View.GONE
            } catch (_: Throwable) {}
        }

        // 亮度条着色
        reg("qs_slider_color") { p, _ ->
            try {
                val color = HookEngine.readInt("qs_brightness_slider_color", 0)
                if (color == 0) return@reg
                val view = p.thisObject ?: return@reg
                val f = view::class.java.getDeclaredField("mProgressDrawable")
                f.isAccessible = true
                (f.get(view) as? android.graphics.drawable.Drawable)?.mutate()?.setTint(color)
            } catch (_: Throwable) {}
        }

        // QS 面板背景模糊 (API31+)
        reg("qs_panel_blur") { p, _ ->
            try {
                val blur = HookEngine.readInt("qs_panel_blur_radius", 0)
                if (blur <= 0) return@reg
                val view = p.thisObject as? android.view.View ?: return@reg
                if (blurInjectedViews.contains(view)) return@reg
                val setter = android.view.View::class.java
                    .getMethod("setBackgroundBlurRadius", Int::class.javaPrimitiveType)
                setter.invoke(view, blur)
                blurInjectedViews.add(view)
            } catch (_: Throwable) {}
        }

        // QS 顶部自定义图
        reg("qs_header_image") { p, _ ->
            try {
                val path = HookEngine.readStr("qs_header_image_path", "")
                if (path.isEmpty()) return@reg
                val view = p.thisObject as? android.view.View ?: return@reg
                if (headerImageViews.contains(view)) return@reg
                val w = (p.args.getOrNull(0) as? Int) ?: return@reg
                val h = (p.args.getOrNull(1) as? Int) ?: return@reg
                if (w <= 0 || h <= 0) return@reg
                val opts = android.graphics.BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                android.graphics.BitmapFactory.decodeFile(path, opts)
                var sample = 1
                while (opts.outWidth / sample > 1280 || opts.outHeight / sample > 480) sample *= 2
                opts.inJustDecodeBounds = false
                opts.inSampleSize = sample
                val bmp = android.graphics.BitmapFactory.decodeFile(path, opts) ?: return@reg
                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, w, h, true)
                val bd = android.graphics.drawable.BitmapDrawable(view.resources, scaled)
                bd.gravity = android.view.Gravity.CENTER
                view.setBackground(bd)
                headerImageViews.add(view)
                if (scaled !== bmp) bmp.recycle()
            } catch (_: Throwable) {}
        }

        // QS 自动注入磁贴: 向结果列表追加扫描到的 TileService
        reg("qs_auto_tiles") { p, ctx ->
            try {
                if (ctx.method.parameterTypes.size < 2 ||
                    ctx.method.parameterTypes[0] != android.content.Context::class.java) return@reg
                if (!HookEngine.readBool("qs_auto_inject_tiles", true)) return@reg
                val specs = p.result as? MutableList<String> ?: return@reg
                val extras = scanTileServices()
                if (extras.isEmpty()) return@reg
                for (spec in extras) if (!specs.contains(spec)) specs.add(spec)
            } catch (_: Throwable) {}
        }

        // QS 磁贴颜色(背景/标签/副标签/图标) —— 按 args[0] 状态给不同色
        reg("qs_color_state") { p, ctx ->
            try {
                // args: [activePref, inactivePref] 或 [singlePref]
                val state = p.args.getOrNull(0) as? Int ?: return@reg
                val activePref = ctx.values.getOrNull(0) ?: return@reg
                val inactivePref = ctx.values.getOrNull(1)
                when (state) {
                    2 -> { val c = HookEngine.readInt(activePref, 0); if (c != 0) p.result = c }
                    1 -> if (inactivePref != null) { val c = HookEngine.readInt(inactivePref, 0); if (c != 0) p.result = c }
                }
            } catch (_: Throwable) {}
        }
        reg("qs_color_flat") { p, ctx ->
            try {
                val pref = ctx.values.getOrNull(0) ?: return@reg
                val c = HookEngine.readInt(pref, 0)
                if (c != 0) p.result = c
            } catch (_: Throwable) {}
        }
        reg("qs_tile_corner") { p, _ ->
            try {
                val cornerRadius = HookEngine.readInt("qs_tile_corner_radius", 0)
                if (cornerRadius == 0) return@reg
                val view = p.thisObject as? android.view.View ?: return@reg
                val f = view::class.java.getDeclaredField("colorBackgroundDrawable")
                f.isAccessible = true
                val d = f.get(view) as? android.graphics.drawable.GradientDrawable ?: return@reg
                d.cornerRadius = cornerRadius * view.resources.displayMetrics.density
            } catch (_: Throwable) {}
        }

        // 锁屏/全局壁纸替换: 返回类型由规则表 params 决定, provider 统一处理
        reg("wallpaper_replace") { p, ctx ->
            try {
                if (ctx.method.parameterTypes.size > 2) return@reg
                val rt = ctx.method.returnType
                if (rt != android.graphics.drawable.Drawable::class.java &&
                    rt != android.graphics.Bitmap::class.java) return@reg
                val custom = loadCustomWallpaper() ?: return@reg
                val isDrawable = ctx.method.returnType == android.graphics.drawable.Drawable::class.java
                if (isDrawable) {
                    p.result = custom
                } else {
                    val bmp = (custom as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bmp != null) p.result = bmp
                }
            } catch (_: Throwable) {}
        }

        // 通知栏/锁屏背景模糊: 对子 ImageView 设 RenderEffect
        reg("notif_bg_blur") { p, _ ->
            try {
                val blur = HookEngine.readInt("notif_bg_blur", 0)
                if (blur <= 0 || android.os.Build.VERSION.SDK_INT < 31) return@reg
                val view = p.thisObject as? android.view.ViewGroup ?: return@reg
                if (view.visibility != android.view.View.VISIBLE) return@reg
                for (i in 0 until view.childCount) {
                    val iv = view.getChildAt(i) as? android.widget.ImageView ?: continue
                    iv.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                        blur.toFloat(), blur.toFloat(), android.graphics.Shader.TileMode.CLAMP))
                }
            } catch (_: Throwable) {}
        }

        // 状态栏反 MDM: 清 disable flags 的 MDM 高位
        reg("commandqueue_unblock") { p, ctx ->
            try {
                val pts = ctx.method.parameterTypes
                val intCount = pts.count { it == Int::class.javaPrimitiveType }
                if (intCount < 2 || pts.lastOrNull() != Boolean::class.javaPrimitiveType) return@reg
                val mdmFlags = 0x01000000 or 0x02000000 or 0x04000000 or 0x08000000 or 0x10000000 or 0x20000000
                for (i in 0 until p.args.size) {
                    val v = p.args[i] as? Int ?: continue
                    if (v and mdmFlags != 0) p.args[i] = v and mdmFlags.inv()
                }
            } catch (_: Throwable) {}
        }

        // ═══════════ TyeLauncherHook (com.toycloud.launcher) ═══════════

        // 桌面壁纸: before 由 block 规则拦, 这里 after 设背景
        reg("launcher_wallpaper") { p, _ ->
            try {
                val callbacks = p.thisObject
                val actField = callbacks::class.java.getDeclaredField("mActivity")
                actField.isAccessible = true
                val activity = actField.get(callbacks) as? android.app.Activity ?: return@reg
                val contentView = try {
                    activity.javaClass.getMethod("getLauncherContentView").invoke(activity) as? android.view.View
                } catch (_: Exception) { null } ?: return@reg
                when {
                    HookEngine.readBool("tyelauncher_follow_system_wallpaper", false) -> {
                        val wallpaper = android.app.WallpaperManager.getInstance(activity).fastDrawable
                        if (wallpaper != null) contentView.background = wallpaper
                    }
                    HookEngine.readBool("tyelauncher_custom_bg", false) -> {}
                    else -> contentView.background = null
                }
            } catch (_: Throwable) {}
        }

        // updateWallPaper: 仅在跟随系统壁纸时拦截(防其清掉壁纸)
        reg("launcher_updatewallpaper") { p, _ ->
            if (HookEngine.readBool("tyelauncher_follow_system_wallpaper", false)) p.result = null
        }

        // 工作区模式: getCardHolderType -> CC_TYPE_WORKSPACE
        reg("launcher_workspace") { p, ctx ->
            // legacy L-3 门控: tyelauncher_workspace_mode 默认关 —— 丢失会导致强制 workspace 模式崩 launcher
            if (!HookEngine.readBool("tyelauncher_workspace_mode", false)) return@reg
            try {
                val enumCls = Class.forName("com.toycloud.launcher.LauncherCardContainerType", true, ctx.classLoader)
                for (c in enumCls.enumConstants ?: emptyArray<Any>()) {
                    val name = c::class.java.getMethod("name").invoke(c) as? String ?: continue
                    if (name == "CC_TYPE_WORKSPACE") { p.result = c; HookEngine.markActive(ctx.ruleId); return@reg }
                }
            } catch (_: Throwable) {}
        }

        // 桌面功能开关: args 为 method=prefKey 映射, 按方法名取 pref 返回布尔
        reg("launcher_feature_flag") { p, ctx ->
            try {
                if (ctx.method.returnType != java.lang.Boolean.TYPE ||
                    ctx.method.parameterTypes.isNotEmpty()) return@reg
                var prefKey: String? = null
                for (kv in ctx.values) {
                    val i = kv.indexOf('=')
                    if (i > 0 && kv.substring(0, i) == ctx.method.name) { prefKey = kv.substring(i + 1); break }
                }
                if (prefKey == null) return@reg
                val raw = HookEngine.readStr(prefKey, "")
                // UI 写入 "1"/"0"; "1".toBoolean()==false 是坑, 必须显式比较
                if (raw.isNotEmpty()) p.result = raw == "1" || raw.equals("true", ignoreCase = true)
            } catch (_: Throwable) {}
        }

        // 桌面布局覆盖: getLayoutId -> R.layout.<override>
        reg("launcher_layout_override") { p, ctx ->
            try {
                val override = HookEngine.readStr("layout_override", "")
                if (override.isEmpty()) return@reg
                val id = Class.forName("com.toycloud.launcher.R\$layout", true, ctx.classLoader)
                    .getField(override).getInt(null)
                p.result = id
            } catch (_: Exception) {}
        }

        // 场景卡片背景 + 自定义卡牌注入
        reg("launcher_custom_cards") { p, ctx ->
            try {
                val cards = p.thisObject
                val bgField = cards::class.java.getDeclaredField("mLandscapBackgroundDrawableName")
                bgField.isAccessible = true
                bgField.set(cards,
                    if (HookEngine.readBool("tyelauncher_custom_bg", false)) "custom_launcher_bg" else "")
                if (HookEngine.readBool("tyelauncher_custom_cards", false)) {
                    val mCardsField = cards::class.java.getDeclaredField("mCards")
                    mCardsField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val mCards = mCardsField.get(cards) as? MutableList<Any> ?: return@reg
                    val jsonFile = java.io.File(
                        android.os.Environment.getExternalStorageDirectory(),
                        "tyelauncher_custom_cards.json")
                    if (jsonFile.exists()) {
                        val raw = jsonFile.readText()
                        val jsonText = raw.trim().trimStart('﻿', '￾', '﻿')
                        if (jsonText.isNotEmpty() && (jsonText.startsWith("[") || jsonText.startsWith("{"))) {
                            val gson = com.google.gson.Gson()
                            val listType = com.google.gson.reflect.TypeToken.getParameterized(
                                MutableList::class.java, Map::class.java).type
                            val list = try {
                                if (jsonText.startsWith("[")) {
                                    gson.fromJson<List<Map<String, Any>>>(jsonText, listType) ?: emptyList()
                                } else {
                                    val single = gson.fromJson<Map<String, Any>>(jsonText,
                                        com.google.gson.reflect.TypeToken.get(Map::class.java).type) ?: emptyMap()
                                    listOf(single)
                                }
                            } catch (_: Exception) { emptyList() }
                            for (entry in list) {
                                try { buildBizCardMeta(entry, ctx.classLoader)?.let { mCards.add(it) } } catch (_: Exception) {}
                            }
                        }
                    }
                    try {
                        val ctx2 = HookEngine.getSystemContext() ?: return@reg
                        for (lkPkg in LkCardManager.getInstalledPackages(ctx2)) {
                            try { buildLkCardMeta(lkPkg, ctx.classLoader)?.let { mCards.add(it) } } catch (_: Exception) {}
                        }
                    } catch (_: Exception) {}
                    HookEngine.markActive("hook_tyelauncher_custom_cards")
                }
            } catch (e: Throwable) { XposedBridge.log("[MDM][Launcher] launcher_custom_cards error: " + e.message) }
        }

        // 学情卡隐藏
        reg("launcher_hide_study_card") { p, _ ->
            try {
                val container = p.thisObject as? android.view.View ?: return@reg
                if (HookEngine.readBool("tyelauncher_hide_study_card", false)) {
                    container.visibility = android.view.View.GONE
                }
            } catch (_: Throwable) {}
        }

        // 自定义卡片创建: BizCardMeta.createInstance -> InterfaceProxy 代理
        reg("launcher_card_create") { p, ctx ->
            try {
                if (!HookEngine.readBool("tyelauncher_custom_cards", false)) return@reg
                val meta = p.thisObject
                val classNameField = meta::class.java.getDeclaredField("mClassName")
                classNameField.isAccessible = true
                val mClassName = classNameField.get(meta) as? String ?: return@reg
                val activity = p.args.getOrNull(0) as? android.app.Activity
                when (mClassName) {
                    "com.leaking.rollupvastsurges.hook.CustomControlCard" -> {
                        val card = CustomControlCard(activity as android.app.Activity, meta)
                        p.result = InterfaceProxy.wrap(card,
                            "com.iflytek.aistudy.launchercard.ILauncherCardPageHolder", ctx.classLoader)
                        HookEngine.markActive(ctx.ruleId)
                    }
                    "com.leaking.rollupvastsurges.hook.LkWebViewCard" -> {
                        if (activity != null) {
                            try {
                                val card = LkWebViewCard(activity, meta)
                                p.result = InterfaceProxy.wrap(card,
                                    "com.iflytek.aistudy.launchercard.ILauncherCardPageHolder", ctx.classLoader)
                            } catch (e: Exception) {
                                XposedBridge.log("[MDM][Launcher] LkWebViewCard create error: " + e.message)
                            }
                        }
                    }
                }
            } catch (e: Throwable) { XposedBridge.log("[MDM][Launcher] createInstance error: " + e.message) }
        }

        // 主题皮肤: applyTheme 后写 Settings
        reg("launcher_skin") { p, _ ->
            try {
                if (!HookEngine.readBool("hook_tyelauncher_skin", false)) return@reg
                val ctx2 = HookEngine.getSystemContext() ?: return@reg
                val themeId = p.args.getOrNull(0) as? String ?: return@reg
                android.provider.Settings.Global.putString(ctx2.contentResolver,
                    "com.iflytek.aistudy.themecenter.theme_system_current_theme",
                    "{\"theme_id\":\"" + themeId + "\"}")
            } catch (_: Throwable) {}
        }

        // 图标包: 按包名换图标(三处目标共用)
        reg("launcher_icon_pack") { p, ctx ->
            try {
                if (!HookEngine.readBool("hook_icon_pack", false)) return@reg
                if (!ensureIconPack()) return@reg
                val pkg = when (val a0 = p.args.getOrNull(0)) {
                    is String -> a0
                    is android.content.pm.LauncherActivityInfo -> a0.applicationInfo?.packageName
                    else -> null
                } ?: return@reg
                if (pkg.isEmpty()) return@reg
                val icon = IconPackManager.getIconDrawable(iconPackRes!!, iconPackPkg!!, pkg) ?: return@reg
                // IconProvider.getIcon 要求 constantState 非空(legacy 行为)
                if (ctx.method.declaringClass.name.endsWith("IconProvider") && icon.constantState == null) return@reg
                p.result = icon
            } catch (_: Throwable) {}
        }
    }

    // ── 共享状态 ──
    private val injectedViews = java.util.WeakHashMap<Any, Boolean>()
    private val blurInjectedViews = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>()))
    private val headerImageViews = java.util.Collections.synchronizedSet(
        java.util.Collections.newSetFromMap(java.util.WeakHashMap<Any, Boolean>()))
    private var injectedSpecs: Set<String>? = null
    private var injectScanMs: Long = 0
    private var cachedWallpaper: android.graphics.drawable.Drawable? = null
    private var cachedWallpaperPath: String = ""
    private var cachedPath: String? = null
    private var cachedDrawable: android.graphics.drawable.Drawable? = null

    private val assetMiss = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private var landscapePrefsMs = 0L
    private var landscapeCfgSet: Set<String> = emptySet()
    private var landscapeAppliedSet: Set<String> = emptySet()
    private var sysWpBmp: android.graphics.Bitmap? = null
    private var sysWpMs = 0L

    /** 反射写字段(含父类链), 兼容私有。 */
    private fun setFieldOrNull(obj: Any, name: String, value: Any?) {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                f.set(obj, value)
                return
            } catch (_: NoSuchFieldException) { c = c.superclass } catch (_: Throwable) { return }
        }
    }

    /** 反射读字段(含父类链)。 */
    private fun getFieldOrNull(obj: Any, name: String): Any? {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f.get(obj)
            } catch (_: NoSuchFieldException) { c = c.superclass } catch (_: Throwable) { return null }
        }
        return null
    }

    /** 读 GlobalLandscapeManager.userLandAppConfig 静态表(反射, 兼容私有)。 */
    @Suppress("UNCHECKED_CAST")
    private fun landscapeMap(cls: Class<*>): MutableMap<String, Any?>? = try {
        val f = cls.getDeclaredField("userLandAppConfig")
        f.isAccessible = true
        f.get(null) as? MutableMap<String, Any?>
    } catch (_: Throwable) { null }

    /** 系统壁纸位图(经 WallpaperManager.getDrawable), 5s 缓存避免热路径反复 IPC。 */
    private fun readSystemWallpaperBitmap(): android.graphics.Bitmap? {
        val now = System.currentTimeMillis()
        sysWpBmp?.let { if (!it.isRecycled && now - sysWpMs < 5000L) return it }
        val b = loadSystemWallpaperBitmap() ?: return null
        sysWpBmp = b
        sysWpMs = now
        return b
    }

    private fun loadSystemWallpaperBitmap(): android.graphics.Bitmap? = try {
        val at = Class.forName("android.app.ActivityThread")
        val app = at.getMethod("currentApplication").invoke(null) as? android.content.Context
        val wm = android.app.WallpaperManager.getInstance(app)
        val d = wm.drawable
        when {
            d is android.graphics.drawable.BitmapDrawable -> {
                val src = d.bitmap
                if (src != null && !src.isRecycled) src.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else null
            }
            d != null -> {
                var w = d.intrinsicWidth
                var h = d.intrinsicHeight
                if (w <= 0) w = 1080
                if (h <= 0) h = 1920
                val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                val cv = android.graphics.Canvas(bmp)
                d.setBounds(0, 0, w, h)
                d.draw(cv)
                bmp
            }
            else -> null
        }
    } catch (_: Throwable) { null }
    private fun prefs(key: String): Set<String> =
        HookEngine.readStr(key, "").split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun hiddenSet(): Set<String>? {
        val raw = HookEngine.readStr("hidden_app_list", "")
        if (raw.isEmpty()) return null
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /** 扫描已安装 app 的 TileService(每 30s 缓存一次)。 */
    private fun scanTileServices(): Set<String> {
        val now = System.currentTimeMillis()
        if (injectedSpecs != null && now - injectScanMs < 30000) return injectedSpecs!!
        try {
            val ctx = HookEngine.getSystemContext() ?: return emptySet()
            val whitelist = HookEngine.readStr("qs_auto_inject_pkgs", "")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val intent = android.content.Intent("android.service.quicksettings.action.QS_TILE")
            val ris = ctx.packageManager.queryIntentServices(intent, 0)
            val specs = mutableSetOf<String>()
            for (ri in ris) {
                val si = ri.serviceInfo ?: continue
                if (whitelist.isNotEmpty() && si.packageName !in whitelist) continue
                if (!si.enabled) continue
                specs.add("custom(" + si.packageName + "/" + si.name + ")")
            }
            injectedSpecs = specs
            injectScanMs = now
            return specs
        } catch (_: Throwable) { return emptySet() }
    }

    // ── 图标包状态 ──
    private var iconPackRes: android.content.res.Resources? = null
    private var iconPackPkg: String? = null

    private fun ensureIconPack(): Boolean {
        // 每次比对当前选中包, 图标包切换后无需重启 launcher 进程
        val selected = HookEngine.readStr("selected_icon_pack", "")
        if (selected.isEmpty()) return false
        if (iconPackPkg == selected && iconPackRes != null) return true
        val ctx = HookEngine.getSystemContext() ?: return false
        val res = IconPackManager.getIconPackResources(ctx.packageManager, selected) ?: return false
        iconPackPkg = selected
        iconPackRes = res
        return true
    }

    // ── 反射构造 BizCardMetaInner -> BizCardMeta ──
    private fun buildBizCardMeta(entry: Map<String, Any>, cl: ClassLoader): Any? {
        return try {
            val innerCls = Class.forName("com.iflytek.aistudy.launchercard.cardloader.inner.BizCardMetaInner", true, cl)
            val inner = innerCls.getDeclaredConstructor().newInstance()
            setStr(inner, "mCardName", entry["cardName"] as? String ?: "custom_card")
            setStr(inner, "mClassName", entry["className"] as? String ?: "com.leaking.rollupvastsurges.hook.CustomControlCard")
            setStr(inner, "mIndicatorName", entry["indicatorName"] as? String ?: "custom_card")
            setStr(inner, "mExtra", "")
            setStr(inner, "mStatPageName", entry["statPageName"] as? String ?: "")
            setBool(inner, "mVirtualCard", (entry["virtualCard"] as? Boolean) ?: false)
            setBool(inner, "mHandleScrollConflict", (entry["handleScrollConflict"] as? Boolean) ?: false)
            val outerCls = Class.forName("com.iflytek.aistudy.launchercard.cardloader.BizCardMeta", true, cl)
            val outerCtor = outerCls.getConstructor(innerCls, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            val pageIndex = (entry["pageIndex"] as? Number)?.toInt() ?: 0
            val isDefault = (entry["isDefault"] as? Boolean) ?: false
            outerCtor.newInstance(inner, pageIndex, isDefault)
        } catch (e: Exception) {
            XposedBridge.log("[MDM][Launcher] buildBizCardMeta error: " + e.message)
            null
        }
    }

    private fun buildLkCardMeta(lkPkg: Map<String, String>, cl: ClassLoader): Any? {
        return try {
            val innerCls = Class.forName("com.iflytek.aistudy.launchercard.cardloader.inner.BizCardMetaInner", true, cl)
            val inner = innerCls.getDeclaredConstructor().newInstance()
            setStr(inner, "mCardName", lkPkg["id"] ?: "lkcard")
            setStr(inner, "mClassName", lkPkg["className"] ?: "com.leaking.rollupvastsurges.hook.LkWebViewCard")
            setStr(inner, "mIndicatorName", lkPkg["id"] ?: "lkcard")
            val outerCls = Class.forName("com.toycloud.launcher.ui.cardloader.BizCardMeta", true, cl)
            val outerCtor = outerCls.getConstructor(innerCls, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
            outerCtor.newInstance(inner, 0, false)
        } catch (e: Exception) {
            XposedBridge.log("[MDM][Launcher] buildLkCardMeta error: " + e.message)
            null
        }
    }

    private fun setStr(obj: Any, field: String, value: String) {
        val f = obj::class.java.getField(field); f.set(obj, value)
    }

    private fun setBool(obj: Any, field: String, value: Boolean) {
        val f = obj::class.java.getField(field); f.setBoolean(obj, value)
    }

    /** 加载并缓存锁屏/全局自定义壁纸(降采样, 目标 1440x2560)。 */
    private fun loadCustomWallpaper(): android.graphics.drawable.Drawable? {
        val path = HookEngine.readStr("keyguard_wallpaper_path", "")
        if (path.isEmpty()) return null
        if (path == cachedWallpaperPath) return cachedWallpaper
        return try {
            val f = java.io.File(path)
            if (!f.exists() || !f.isFile) return null
            val opts = android.graphics.BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            android.graphics.BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > 1440 || opts.outHeight / sample > 2560) sample *= 2
            opts.inJustDecodeBounds = false
            opts.inSampleSize = sample
            val bmp = android.graphics.BitmapFactory.decodeFile(path, opts) ?: return null
            (cachedWallpaper as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
            cachedWallpaper = android.graphics.drawable.BitmapDrawable(null, bmp)
            cachedWallpaperPath = path
            cachedWallpaper
        } catch (_: Throwable) { null }
    }

    /** 解码并缓存背景图(降采样, 目标 1600x2560)。 */
    private fun loadRecentsBg(): android.graphics.drawable.Drawable? {
        val path = HookEngine.readStr("recents_bg_path", "")
        if (path.isEmpty()) return null
        if (path == cachedPath) return cachedDrawable
        return try {
            val f = java.io.File(path)
            if (!f.exists() || !f.isFile) return null
            val opts = android.graphics.BitmapFactory.Options()
            opts.inJustDecodeBounds = true
            android.graphics.BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 2560) sample *= 2
            opts.inJustDecodeBounds = false
            opts.inSampleSize = sample
            val bmp = android.graphics.BitmapFactory.decodeFile(path, opts) ?: return null
            (cachedDrawable as? android.graphics.drawable.BitmapDrawable)?.bitmap?.recycle()
            cachedDrawable = android.graphics.drawable.BitmapDrawable(null, bmp)
            cachedPath = path
            cachedDrawable
        } catch (_: Throwable) { null }
    }
}
