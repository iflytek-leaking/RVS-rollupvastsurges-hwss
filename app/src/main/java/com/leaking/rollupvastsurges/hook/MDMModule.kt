package com.leaking.rollupvastsurges.hook

import android.app.Activity
import android.content.Intent
import com.leaking.rollupvastsurges.BuildConfig
import com.leaking.rollupvastsurges.backend.NativeRules
import com.leaking.rollupvastsurges.compat.LoadPackageParam
import com.leaking.rollupvastsurges.compat.MethodHookParam
import com.leaking.rollupvastsurges.compat.XC_MethodHook
import com.leaking.rollupvastsurges.compat.XposedBridge
import com.leaking.rollupvastsurges.contract.ResolverMode
import com.leaking.rollupvastsurges.resolver.ApkPaths
import com.leaking.rollupvastsurges.resolver.HookInstaller
import com.leaking.rollupvastsurges.resolver.ResolverFactory
import com.leaking.rollupvastsurges.resolver.ResolverState
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

// 校验类日志仅在 debug 构建输出; release 静默(防逆向者从日志定位校验点)
internal inline fun secLog(msg: String) {
    if (BuildConfig.DEBUG) XposedBridge.log(msg)
}

/**
 * Xposed 模块入口 — libxposed API 102。
 *
 * 与 legacy 的架构差异:
 *  - 没有 initZygote: API 101+ 模块只在 scope 进程内加载，全局 hook 移到 onModuleLoaded。
 *  - handleLoadPackage → onPackageLoaded / onSystemServerStarting。
 *  - 元数据在 META-INF/xposed/ (java_init.list / scope.list / module.prop)。
 */
class MDMModule : XposedModule() {

    private companion object {
        // 完整性校验只跑一次（跨进程静态缓存）
        @Volatile var integrityChecked = false
    }

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        XposedBridge.activeModule = this
        try {
            XposedBridge.log("[MDM] ========== onModuleLoaded process=${param.processName} systemServer=${param.isSystemServer} ==========")
            if (HookEngine.checkTimeBomb()) {
                secLog("[MDM] TIME-BOMB: expired, all hooks skipped")
                return
            }
            if (isExpiredHardened()) {
                secLog("[MDM] native TIME-BOMB: expired, all hooks skipped")
                return
            }

            // ── 原 initZygote 的全局 hook（框架类，进程启动早期即可用）──
            // UniversalHook 已整体迁入 native 规则表(scope="*"), Kotlin 源已删除
            XposedBridge.log("[MDM] PenButtonHook...")
            runCatching { PenButtonHook.setup() }
            XposedBridge.log("[MDM] Binder hooks...")
            installBinderHooks()

            // 触发各 BaseHook 子类自注册（子类 init { HookRegistry.register(this) }）
            // 注: SystemUI/TyeLauncher/Quickstep/AppListReport/ThemeCenter 已整体迁到
            //     规则表+provider(resolver/ActionProviders), Java 源已删除, 不再自注册;
            //     HWC 服务 hook 也进规则表(HWCHook 原先是未实例化的死代码, 迁移后由
            //     dispatchRuleTable 按 scope=hwc.service/hwc 激活, 各 hook_hwc_* 开关可控)。
            runCatching { MDMHook() }
            runCatching { SystemHook() }
            XposedBridge.log("[MDM] ========== onModuleLoaded DONE ==========")
        } catch (t: Throwable) {
            XposedBridge.log("[MDM] ---------- onModuleLoaded CRASH: ${t.javaClass.name}: ${t.message} ----------")
        }
    }

    /** 包加载完成、默认 classloader 就绪（Android 10+）。 */
    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        XposedBridge.activeModule = this
        dispatch(param.packageName, param.defaultClassLoader)
    }

    /** system_server：取代第一个包的 onPackageLoaded。 */
    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        XposedBridge.activeModule = this
        dispatch("android", param.classLoader)
    }

    /** 目标包分发（等价于 legacy 的 handleLoadPackage 主体）。 */
    private fun dispatch(pkg: String, classLoader: ClassLoader) {
        try {
            if (HookEngine.checkTimeBomb()) return

            // 防二次打包: 仅校验一次, 失败静默失效(不弹窗, 防逆向定位)
            if (!integrityChecked) {
                integrityChecked = true
                if (!isIntegrityHardened(BuildConfig.RELEASE_SIG_SHA256, BuildConfig.RELEASE_DEX_CRC32)) {
                    secLog("[MDM] module integrity check failed, hooks disabled")
                    return
                }
            }
            // native 层时间炸弹(防 Java 层被 patch 绕过)
            if (isExpiredHardened()) return

            // 崩溃保护: 先检查再计数(顺序不能反, 否则冷却重置分支永不可达)
            if (HookEngine.shouldSkip(pkg)) {
                XposedBridge.log("[MDM] SKIP $pkg (crash protection)")
                return
            }
            if (pkg in HookEngine.TARGET_PKGS) HookEngine.incrementCrash(pkg)

            // ── 后端规则表优先(P4, 默认 dexkit): 成功接管则 legacy 组循环让位, 避免双重 hook ──
            runCatching { dispatchRuleTable(pkg, classLoader) }

            val lpparam = LoadPackageParam(pkg, classLoader)
            val hooks = HookRegistry.forPackage(pkg)
            if (hooks.isNotEmpty()) {
                for (hook in hooks) {
                    XposedBridge.log("[MDM] Dispatch $pkg → ${hook.javaClass.simpleName}")
                    hook.register(lpparam)
                }
            } else if (pkg in HookEngine.TARGET_PKGS) {
                XposedBridge.log("[MDM] WARN: no hook registered for $pkg")
            }


            if (pkg in HookEngine.TARGET_PKGS && HookEngine.readBool("show_inject_notify", true)) {
                installInjectNotify(pkg)
            }
        } catch (t: Throwable) {
            XposedBridge.log("[MDM] >>> Dispatch $pkg CRASH: ${t.javaClass.name}: ${t.message}")
        }
    }

    /**
     * 后端规则表驱动的目标发现 —— 前后端分离的落地:
     *  - 规则(目标类名/锚点)全部来自 libpenhook.so 的加密表, Java 侧无硬编码
     *  - native 模式: 按类名直连; dexkit 模式: 锚点发现(ROM 适配)
     * 默认 static = 不启用, 走上面的 legacy 硬编码路径。
     */
    private fun dispatchRuleTable(pkg: String, classLoader: ClassLoader) {
        // legacy 已删除, resolver 是唯一路径: 非法 mode 回落默认(dexkit), 不允许"无 hook"
        var mode = HookEngine.readStr(ResolverMode.PREF, ResolverMode.DEFAULT)
        if (mode != ResolverMode.NATIVE && mode != ResolverMode.DEXKIT) mode = ResolverMode.DEFAULT
        val all = NativeRules.load()
        if (all.isEmpty()) {
            // native 规则表加载失败 —— legacy 已删除, 这里只能大声告警
            XposedBridge.log("[MDM][Resolver] !! 规则表为空(native 加载失败), 本进程 " + pkg + " 无任何规则 hook")
            return
        }
        // 本进程相关的规则(scope 为空 = 默认 MDM_PKGS; "*" = 通配所有 scope.list 进程)
        val rules = all.filter { r ->
            val sc: Collection<String> = if (r.scope.isEmpty()) HookEngine.MDM_PKGS else r.scope
            "*" in sc || pkg in sc
        }
        if (rules.isEmpty()) return
        val resolver = ResolverFactory.create(mode, rules) { XposedBridge.log(it) } ?: return
        val apkPath = if (mode == ResolverMode.DEXKIT) ApkPaths.sourceDir(pkg) else null
        val t0 = System.currentTimeMillis()
        val specs = resolver.resolve(pkg, classLoader, apkPath)
        val cost = System.currentTimeMillis() - t0
        if (specs.isEmpty()) {
            XposedBridge.log("[MDM][Resolver] " + pkg + " mode=" + mode + " -> 0 specs (" + cost + "ms)")
            return
        }
        // per-group 开关必须生效 —— ruleId 即 legacy 的 prefKey
        // 另: 少数规则 legacy 是"配置了才装 hook", 规则表是"永远装", 这里补回安装期判定
        val enabled = specs.filter { HookEngine.readBool(it.ruleId, true) && ruleConfigured(it.ruleId) }
        if (enabled.isEmpty()) {
            XposedBridge.log("[MDM][Resolver] " + pkg + " 所有组被用户关闭 (" + cost + "ms)")
            return
        }
        val installed = HookInstaller.install(enabled, classLoader) { XposedBridge.log(it) }
        val n = installed.values.sum()
        if (n > 0) ResolverState.markHandled(pkg)
        // 与 legacy 对齐: 每组记录 active/failed, 供 UI 显示
        enabled.map { it.ruleId }.distinct().forEach { rid ->
            if ((installed[rid] ?: 0) > 0) HookEngine.markActive(rid) else HookEngine.markFailed(rid)
        }
        XposedBridge.log("[MDM][Resolver] " + pkg + " mode=" + mode + " specs=" + specs.size +
            " enabled=" + enabled.size + " installed=" + n + " (" + cost + "ms)")
    }

    /**
     * 安装期配置判定 —— legacy 里部分 hook 是"配置了才装":
     * quickstep recents 背景 legacy 仅在 recents_bg_path 非空时装 hook。
     * 规则表是"永远装", 若不补回, RecentsView.onAttachedToWindow(launcher 启动即触发)
     * 会在 UI 线程反复同步 IPC 读 pref -> ANR/崩溃。这里恢复 legacy 语义。
     */
    private fun ruleConfigured(ruleId: String): Boolean = when (ruleId) {
        "hook_quickstep_recents_bg" -> HookEngine.readStr("recents_bg_path", "").isNotEmpty()
        // Activity.onCreate 极热, legacy 是"开了才装 hook"
        "hook_uni_force_landscape" -> HookEngine.readBool("force_landscape", false)
        else -> true
    }

    // ── Binder 层拦截（原 initZygote 内容）──
    private fun installBinderHooks() {
        try {
            // Android 10+ (API 29): moveTaskToFront/Back → IActivityTaskManager
            // Pre-10: all on IActivityManager
            val binderClasses = listOf(
                "android.app.IActivityManager\$Stub\$Proxy",
                "android.app.IActivityTaskManager\$Stub\$Proxy")
            var count = 0
            for (cn in binderClasses) {
                try {
                    val proxy = Class.forName(cn)
                    for (m in proxy.methods) {
                        if (m.name !in setOf("forceStopPackage", "killBackgroundProcesses",
                                "removeTask", "moveTaskToFront", "moveTaskToBack",
                                "startActivity", "startActivityAsUser")) continue
                        XposedBridge.hookMethod(m, object : XC_MethodHook() {
                            override fun beforeHookedMethod(param: MethodHookParam) {
                                try {
                                    if (m.name == "removeTask") { param.result = null; return }
                                    // startActivity/AsUser: 拦截拉起 Launcher 的操作
                                    if (m.name in listOf("startActivity", "startActivityAsUser")) {
                                        val intent = param.args.firstOrNull { it is Intent } as? Intent ?: return
                                        val pkg = intent.component?.packageName ?: return
                                        if (pkg == "com.toycloud.launcher") { param.result = null; return }
                                    }
                                    val pkg = param.args[0] as? String
                                    if (pkg != null && (pkg in HookEngine.TARGET_PKGS || pkg == HookEngine.MODULE_PKG)) {
                                        param.result = null; return
                                    }
                                    if (m.name in listOf("moveTaskToFront", "moveTaskToBack")) { param.result = null; return }
                                    if (pkg != null) param.result = null
                                } catch (_: Throwable) {}
                            }
                        })
                        count++
                    }
                } catch (_: Throwable) {}  // LinkageError/NoClassDefFoundError 也是 Throwable
            }
            XposedBridge.log("[MDM] Binder hooks: $count methods")
        } catch (e: Throwable) {
            XposedBridge.log("[MDM] Binder hooks FAILED: ${e.message}")
        }
    }

    // ── 注入通知（原 handleLoadPackage 末尾）──
    private fun installInjectNotify(pkg: String) {
        try {
            XposedBridge.hookAllMethods(Activity::class.java, "onCreate", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val act = param.thisObject as? Activity ?: return
                    if (act.packageName != pkg) return
                    if (!HookEngine.notifiedPkgs.add(pkg)) return
                    HookEngine.showInjectionNotify(act, pkg)
                }
            })
        } catch (_: Throwable) {}
    }
}
