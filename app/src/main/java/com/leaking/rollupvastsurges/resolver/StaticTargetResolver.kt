package com.leaking.rollupvastsurges.resolver

import com.leaking.rollupvastsurges.contract.HookSpec
import com.leaking.rollupvastsurges.contract.RuleRecord
import com.leaking.rollupvastsurges.contract.TargetResolver

/**
 * 按规则表里的类名直连。ROM 未混淆/类名未变时最稳、最快。
 * 类名仍来自 native 规则表，Java 侧没有硬编码。
 */
class StaticTargetResolver(private val rules: List<RuleRecord>) : TargetResolver {
    override fun resolve(packageName: String, classLoader: ClassLoader, apkPath: String?): List<HookSpec> {
        val out = ArrayList<HookSpec>()
        for (r in rules) {
            for (cn in r.classes) {
                val cls = try { Class.forName(cn, false, classLoader) } catch (_: Throwable) { continue }
                for (m in cls.declaredMethods) {
                    val ptypes = m.parameterTypes.map { it.name }
                    if (!MethodFilter.matchesRule(m.name, ptypes, r.methods, r.params)) continue
                    out.add(
                        HookSpec(
                            ruleId = r.ruleId,
                            className = cn,
                            methodName = m.name,
                            paramTypes = ptypes,
                            returnType = m.returnType.name,
                            action = r.action,
                        )
                    )
                }
            }
        }
        return out
    }
}
