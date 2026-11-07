package com.leaking.rollupvastsurges.resolver

import com.leaking.rollupvastsurges.compat.MethodHookParam
import com.leaking.rollupvastsurges.compat.XC_MethodHook
import com.leaking.rollupvastsurges.compat.XposedBridge
import com.leaking.rollupvastsurges.contract.ActionSpec
import com.leaking.rollupvastsurges.contract.HookSpec
import java.lang.reflect.Method

/**
 * 前端执行器: 把后端给的 [HookSpec] 解析成 Method 并安装。
 * 只按 [ActionSpec] 执行，不含任何业务/目标知识。
 *
 * 支持的能力(与 legacy 手工 hook 等价):
 *   ① 无条件阻断, 结果按返回类型自适应        (legacy 的 when(m.returnType))
 *   ② 无条件阻断, 返回固定字面量              (legacy 的 param.result = true)
 *   ③ 仅当 param.args[i] 满足条件才阻断        (legacy 的 if (args[1] == true))
 *     条件算子: eq(等于) / in(属于集合) / pfx(前缀) / has(包含)
 */
object HookInstaller {
    /** 返回 ruleId -> 成功安装数(UI 状态显示与 per-group 开关对齐 legacy)。 */
    fun install(specs: List<HookSpec>, classLoader: ClassLoader, log: (String) -> Unit = {}): Map<String, Int> {
        val byRule = LinkedHashMap<String, Int>()
        for (s in specs) {
            val m = try { resolveMethod(s, classLoader) } catch (_: Throwable) { null }
            if (m == null) continue
            val a = s.action
            try {
                if (a.isProvider) {
                    val fn = ActionProviders.get(a.provider)
                    if (fn == null) {
                        log("[Install] 未注册的 provider: " + a.provider)
                        continue
                    }
                    val ctx = ActionContext(s.ruleId, a.args, m, log)
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (a.runAfter || !condOk(a, param)) return
                            invokeProvider(fn, param, ctx, a)
                        }
                        override fun afterHookedMethod(param: MethodHookParam) {
                            if (!a.runAfter || !condOk(a, param)) return
                            invokeProvider(fn, param, ctx, a)
                        }
                    })
                } else {
                    XposedBridge.hookMethod(m, object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            if (!a.isBlock) return
                            if (!condOk(a, param)) return
                            val bv = blockValue(a, m.returnType)
                            if (bv.first) param.result = bv.second
                        }
                    })
                }
                byRule[s.ruleId] = (byRule[s.ruleId] ?: 0) + 1
            } catch (t: Throwable) {
                log("[Install] failed " + s.className + "." + s.methodName + ": " + t.message)
            }
        }
        return byRule
    }

    private fun invokeProvider(
        fn: (MethodHookParam, ActionContext) -> Unit,
        param: MethodHookParam,
        ctx: ActionContext,
        a: ActionSpec,
    ) {
        try {
            fn(param, ctx)
        } catch (t: Throwable) {
            ctx.log("[Provider] " + a.provider + " failed: " + t.message)
        }
    }

    // ── 条件求值 ──
    private fun condOk(a: ActionSpec, param: MethodHookParam): Boolean {
        if (!a.isConditional) return true
        val arr = param.args ?: return false
        if (a.condArg >= arr.size) return false
        val actual = arr[a.condArg]
        return when (a.condOp) {
            "eq" -> a.condValues.size == 1 && litEq(actual, a.condValues[0])
            "in" -> a.condValues.any { litEq(actual, it) }
            "pfx" -> a.condValues.any { actual is String && it.isNotEmpty() && actual.startsWith(it) }
            "has" -> a.condValues.any { actual is String && it.isNotEmpty() && actual.contains(it) }
            else -> true
        }
    }

    /** 字面量比较: 支持 null / true / false / 整数 / 字符串。 */
    private fun litEq(actual: Any?, lit: String): Boolean = when (lit) {
        "null" -> actual == null
        "true" -> actual == true
        "false" -> actual == false
        else -> {
            val n = lit.toLongOrNull()
            if (n != null) (actual as? Number)?.toLong() == n else actual == lit
        }
    }

    /**
     * 结果计算。
     * ret 为空      -> 标准自适应(legacy 的 when(m.returnType))
     * ret 非空      -> "类型=字面量" 列表(VS 分隔), 类型可为 * 表示任意;
     *                  未匹配的类型 -> null。legacy 有 3 种策略:
     *                    "boolean=true"  (A9 forbiddenUrlForPackages)
     *                    "boolean=false" (NetworkControlService)
     *                    "*=true" / "*=null" (canLaunch / firewall)
     */
    /**
     * 结果计算, 返回 (是否拦截, 拦截值)。
     * ret 为空  -> 标准自适应, 只对 void/boolean/int/long/String 拦截;
     *              对象返回类型不拦截(对齐 legacy 的 when(m.returnType) 无 else 分支, 防给调用方塞 null 引发 NPE)。
     * ret 非空  -> "类型=字面量" 列表(VS 分隔), "*" 匹配任意; 未匹配的类型 -> 不拦截。
     */
    private fun blockValue(a: ActionSpec, t: Class<*>): Pair<Boolean, Any?> {
        val map = a.ret
        if (!map.isNullOrEmpty()) {
            val tn = t.name
            for (pair in map.split(4.toChar())) {
                val i = pair.indexOf("=")
                if (i <= 0) continue
                val k = pair.substring(0, i)
                if (k == "*" || k == tn) return true to literal(pair.substring(i + 1))
            }
            return false to null
        }
        return adaptive(t)
    }

    /** 字面量 -> 运行时值。 */
    private fun literal(s: String): Any? = when (s) {
        "null" -> null
        "true" -> true
        "false" -> false
        "0" -> 0
        "0L" -> 0L
        "\"\"" -> ""
        "[]" -> ArrayList<Any>()
        else -> s
    }

    /** 自适应默认值, 返回 (是否拦截, 值)。对象返回类型 -> 不拦截(对齐 legacy)。 */
    private fun adaptive(t: Class<*>): Pair<Boolean, Any?> = when (t) {
        Boolean::class.javaPrimitiveType -> true to false
        Int::class.javaPrimitiveType -> true to 0
        Long::class.javaPrimitiveType -> true to 0L
        String::class.java -> true to ""
        Void.TYPE -> true to null
        else -> false to null
    }

    private fun resolveMethod(s: HookSpec, cl: ClassLoader): Method? {
        val cls = Class.forName(s.className, false, cl)
        val types = ArrayList<Class<*>>(s.paramTypes.size)
        for (n in s.paramTypes) types.add(nameToClass(n, cl) ?: return null)
        return cls.getDeclaredMethod(s.methodName, *types.toTypedArray()).also { it.isAccessible = true }
    }

    private fun nameToClass(name: String, cl: ClassLoader): Class<*>? = when (name) {
        "void" -> Void.TYPE
        "boolean" -> Boolean::class.javaPrimitiveType
        "byte" -> Byte::class.javaPrimitiveType
        "char" -> Char::class.javaPrimitiveType
        "short" -> Short::class.javaPrimitiveType
        "int" -> Int::class.javaPrimitiveType
        "long" -> Long::class.javaPrimitiveType
        "float" -> Float::class.javaPrimitiveType
        "double" -> Double::class.javaPrimitiveType
        else -> if (name.endsWith("[]")) {
            val comp = nameToClass(name.substring(0, name.length - 2), cl) ?: return null
            java.lang.reflect.Array.newInstance(comp, 0).javaClass
        } else Class.forName(name, false, cl)
    }
}
