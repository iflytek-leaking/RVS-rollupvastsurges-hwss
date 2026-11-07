package com.leaking.rollupvastsurges.resolver

import com.leaking.rollupvastsurges.contract.HookSpec
import com.leaking.rollupvastsurges.contract.RuleRecord
import com.leaking.rollupvastsurges.contract.TargetResolver
import org.luckypray.dexkit.DexKitBridge

/**
 * DexKit 锚点发现 —— 前后端分离的核心。
 *
 * 离线实测结论(见 tools/rulecheck/validate_rules.py):
 *   纯锚点召回仅 ~19% —— 大量目标是 getter/setter, 方法体内没有任何字符串,
 *   usingStrings 永远找不到它们。所以锚点**不能替代**类名直连。
 *
 * 因此采用 类名直连 ∪ 锚点补发现:
 *   ① 类名直连: 类名未变时全覆盖(也是 apkPath 缺失时的降级路径)
 *   ② 锚点补发现: 类名被混淆/改名时, 仍能按"方法用了哪些字符串"找到目标
 * 两者按 (类名, 方法名, 参数) 去重合并。
 */
class DexKitTargetResolver(
    private val rules: List<RuleRecord>,
    private val log: (String) -> Unit = {},
) : TargetResolver {

    private val classList = StaticTargetResolver(rules)

    override fun resolve(packageName: String, classLoader: ClassLoader, apkPath: String?): List<HookSpec> {
        // ① 类名直连
        val out = ArrayList<HookSpec>(classList.resolve(packageName, classLoader, apkPath))
        val seen = HashSet<String>()
        for (s in out) seen.add(sig(s))
        if (apkPath.isNullOrEmpty()) return out
        // 所有规则都没有锚点(如 system_server 的框架类) -> 建桥纯属浪费
        if (rules.none { it.anchors.isNotEmpty() }) return out

        // ② 锚点补发现(去重合并)
        var bridge: DexKitBridge? = null
        try {
            bridge = DexKitBridge.create(apkPath)
            for (r in rules) {
                if (r.anchors.isEmpty()) continue
                try {
                    val methods = bridge.findMethod {
                        if (r.packages.isNotEmpty()) searchPackages(r.packages)
                        matcher { usingStrings(r.anchors) }
                    }
                    var added = 0
                    for (m in methods) {
                        val ptypes = m.paramTypeNames.toList()
                        if (!MethodFilter.matchesRule(m.name, ptypes, r.methods, r.params)) continue
                        val spec = HookSpec(
                            ruleId = r.ruleId,
                            className = m.declaredClassName,
                            methodName = m.name,
                            paramTypes = ptypes,
                            returnType = m.returnTypeName,
                            action = r.action,
                        )
                        if (seen.add(sig(spec))) {
                            out.add(spec)
                            added++
                        }
                    }
                    log("[DexKit] " + r.ruleId + ": " + r.anchors.size + " anchors, +" + added + " new")
                } catch (t: Throwable) {
                    log("[DexKit] " + r.ruleId + " query failed: " + t.message)
                }
            }
        } catch (t: Throwable) {
            log("[DexKit] bridge create failed: " + t.message)
        } finally {
            try { bridge?.close() } catch (_: Throwable) {}
        }
        return out
    }

    private fun sig(s: HookSpec): String =
        s.className + "#" + s.methodName + "(" + s.paramTypes.joinToString(",") + ")"
}
