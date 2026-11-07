package com.leaking.rollupvastsurges.resolver

/**
 * 目标方法名过滤。
 * - 规则给了显式方法名表(SystemHook 那类) -> 精确匹配
 * - 否则用复刻 legacy MDMHook 的启发式
 * 放在 Java 侧而不是 DexKit 正则里: 保证两种 resolver 语义完全一致。
 */
internal object MethodFilter {
    private val EXCLUDE = setOf(
        "getClass", "hashCode", "equals", "toString",
        "notify", "notifyAll", "wait", "clone", "finalize",
    )
    private val PREFIX = arrayOf(
        "set", "enable", "disable", "forbid", "allow", "clean", "report",
        "write", "clear", "add", "remove", "update", "force", "kill",
        "stop", "start", "load", "get", "show",
    )

    /**
     * 规则级方法匹配: 名字在 [explicit] 里, 且参数签名满足 [params] 同序约束。
     * [params] 为空 -> 不过滤; 条目 "*" -> 任意; 否则逗号分隔的类型名精确匹配。
     */
    fun matchesRule(
        name: String,
        paramTypeNames: List<String>,
        explicit: List<String>,
        params: List<String>,
    ): Boolean {
        if (explicit.isNotEmpty()) {
            for (i in explicit.indices) {
                val e = explicit[i]
                // 条目以 * 结尾 -> 前缀匹配(如 send* / a*); 否则精确匹配
                val hit = if (e.endsWith("*")) name.startsWith(e.dropLast(1)) else name == e
                if (!hit) continue
                val want = params.getOrNull(i) ?: return true
                if (want.isEmpty() || want == "*") return true
                return paramTypeNames.joinToString(",") == want
            }
            return false
        }
        return matches(name)
    }

    /** [explicit] 非空时精确匹配; 为空时走启发式。 */
    fun matches(name: String, explicit: List<String> = emptyList()): Boolean {
        if (explicit.isNotEmpty()) return name in explicit
        // "access" + 0x24('$') 开头的是编译器合成方法
        if (name.startsWith("<")) return false
        if (name.startsWith("access") && name.getOrNull(6)?.code == 0x24) return false
        if (name in EXCLUDE) return false
        if (name.length == 1) return true
        for (p in PREFIX) if (name.startsWith(p)) return true
        return name == "onReceive" || name == "onChange" || name == "invoke"
    }
}
