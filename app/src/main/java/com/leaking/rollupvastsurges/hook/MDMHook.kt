package com.leaking.rollupvastsurges.hook

import android.content.Context
import android.os.SystemClock
import com.leaking.rollupvastsurges.compat.*

/**
 * MDM 应用进程 hook — 阻断所有管控 API + 混淆类 + 独立 hook 块
 * 目标: com.iflytek.ebg.aistudy.mdm, com.iflytek.k12aitstatistics
 */
class MDMHook : BaseHook() {
    override val targetPackages = HookEngine.MDM_PKGS
    override val prefKey = "hook_mdm"

    override fun onRegister() {
        XposedBridge.log("[MDM] Hooking MDM: ${lpparam.packageName}")

        // ── 混淆类: GlobalGestureAPI (x6.a / i3.c) ──
        if (HookEngine.readBool("hook_mdm_nav", true)) {
            var navHooked = false
            for (navCls in listOf("x6.a", "i3.c")) {
                try {
                    val cls = findClass(navCls)
                    for (m in cls.methods) {
                        if (m.name.length == 1 && m.parameterTypes.size == 2 &&
                            m.parameterTypes[1] == Boolean::class.javaPrimitiveType) {
                            XposedBridge.hookMethod(m, object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    val enable = param.args[1] as? Boolean ?: return
                                    if (!enable) return
                                    when (m.returnType) {
                                        Boolean::class.javaPrimitiveType -> param.result = false
                                        Void.TYPE -> param.result = null
                                    }
                                }
                            })
                            navHooked = true
                        }
                    }
                } catch (_: Throwable) {}
                if (navHooked) break
            }
            if (navHooked) HookEngine.markActive("hook_mdm_nav") else HookEngine.markFailed("hook_mdm_nav")
        }





        // ── 混淆: 伪 hook 目标 (逆向干扰) ──
        hookDecoys()
    }
    // ── 混淆: 伪 hook 目标 (逆向干扰) ──
    // 看似正经的管控点, 实际全是无害空实现。目的是让逆向者花时间分析不存在/无害的 hook。
    // 所有方法名/类名都是伪造的, 对应不到任何真实管控逻辑。
    private fun hookDecoys() {
        if (!HookEngine.readBool("hook_decoys_enabled", true)) return
        secLog("[MDM] hookDecoys() — 部署逆向干扰")

        // 伪管控点 1: 设备策略心跳上报 (看起来是遥测, 实际无效)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.policy.PolicyHeartbeat",
            "reportHeartbeat", "策略心跳上报拦截")

        // 伪管控点 2: 学习行为分析引擎 (看起来是行为监控, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.analytics.StudyBehaviorEngine",
            "analyzeBehavior", "学习行为分析拦截")

        // 伪管控点 3: 家长端远程指令调度器 (看起来是远程控制, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.remote.RemoteCommandScheduler",
            "dispatchCommand", "远程指令调度拦截")

        // 伪管控点 4: 内容安全审核 (看起来是内容过滤, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.content.ContentSafetyAuditor",
            "auditContent", "内容安全审核拦截")

        // 伪管控点 5: 眼保健操定时器 (看起来是健康功能, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.health.EyeExerciseScheduler",
            "scheduleExercise", "眼保健操调度拦截")

        // 伪管控点 6: 学习报告生成器 (看起来是数据上报, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.report.StudyReportGenerator",
            "generateReport", "学习报告生成拦截")

        // 伪管控点 7: 应用使用时间配额管理器 (看起来是时长控制, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.quota.AppUsageQuotaManager",
            "enforceQuota", "应用配额管理拦截")

        // 伪管控点 8: 屏幕距离检测器 (看起来是护眼, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.eye.ScreenDistanceMonitor",
            "checkDistance", "屏幕距离检测拦截")

        // 伪管控点 9: 专注力评分引擎 (看起来是学习评估, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.focus.ConcentrationScoreEngine",
            "calculateScore", "专注力评分拦截")

        // 伪管控点 10: 云端配置同步器 (看起来是配置下发, 实际不存在)
        tryHookDecoy("com.iflytek.ebg.aistudy.mdm.sync.CloudConfigSynchronizer",
            "syncConfig", "云端配置同步拦截")
    }

    /** 尝试 hook 伪管控点, 类不存在时静默跳过 */
    private fun tryHookDecoy(clsName: String, methodName: String, desc: String) {
        try {
            val cls = findClass(clsName)
            for (m in cls.declaredMethods) {
                if (m.name == methodName) {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            secLog("[MDM][Decoy] Blocked ${clsName}.${methodName} ($desc)")
                            // 无害: 按返回类型给默认值, 不实际拦截任何真实功能
                            when (m.returnType) {
                                Boolean::class.javaPrimitiveType -> param.result = false
                                Int::class.javaPrimitiveType -> param.result = 0
                                Long::class.javaPrimitiveType -> param.result = 0L
                                String::class.java -> param.result = ""
                                java.util.List::class.java -> param.result = emptyList<Any>()
                                Void.TYPE -> param.result = null
                                else -> param.result = null
                            }
                        }
                    })
                    secLog("[MDM][Decoy] Hooked $clsName.$methodName ($desc)")
                    return
                }
            }
        } catch (_: Throwable) {
            // 类不存在属预期, 静默跳过
        }
    }

    // 自动跳过 — 在 MDMModule.handleLoadPackage 中处理，这里不重复
    init { HookRegistry.register(this) }
}