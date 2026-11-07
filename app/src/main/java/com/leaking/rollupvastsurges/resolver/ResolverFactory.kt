package com.leaking.rollupvastsurges.resolver

import com.leaking.rollupvastsurges.backend.NativeRules
import com.leaking.rollupvastsurges.contract.ResolverMode
import com.leaking.rollupvastsurges.contract.TargetResolver

/**
 * resolver 工厂 —— 前端唯一的后端选择点。
 * 返回 null 表示走 legacy 硬编码路径（默认，未启用时不改变现有行为）。
 */
object ResolverFactory {
    /**
     * [rules] 由调用方按进程 scope 过滤后传入。
     * 返回 null 表示无可用规则(已迁移的 hook 不再有 legacy 兜底, 调用方负责告警)。
     */
    fun create(mode: String, rules: List<com.leaking.rollupvastsurges.contract.RuleRecord>, log: (String) -> Unit = {}): TargetResolver? {
        if (mode != ResolverMode.NATIVE && mode != ResolverMode.DEXKIT) return null
        if (rules.isEmpty()) return null
        return if (mode == ResolverMode.DEXKIT) DexKitTargetResolver(rules, log)
        else StaticTargetResolver(rules)
    }
}
