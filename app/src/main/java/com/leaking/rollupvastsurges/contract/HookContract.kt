package com.leaking.rollupvastsurges.contract

/**
 * ── 前端(Java) / 后端(native+DexKit) 分离契约 ──
 *
 * 后端只产出 [HookSpec] 列表；前端只负责安装与执行。
 * 前端不包含任何 iFLYTEK 类名/锚点字符串（那些全部在 native 加密规则表里）。
 */

/**
 * 前端执行动作 —— 后端只给"做什么"，前端负责执行。
 *
 * 线上编码（FS = U+0003，VS = U+0004）:
 *   kind FS ret FS condArg FS condOp FS values
 *     kind     block | pass
 *     ret      空 = 按返回类型自适应; 否则字面量
 *     condArg  -1 = 无条件; 否则参数下标
 *     condOp   eq | in | pfx | has
 *     values   VS 分隔
 *
 * 兼容旧格式: 无 FS 时按 legacy 枚举名解析(BLOCK_BY_RETURN / BLOCK_VOID / PASS)。
 */
data class ActionSpec(
    val kind: String = KIND_BLOCK,
    val ret: String? = null,
    val condArg: Int = -1,
    val condOp: String = "",
    val condValues: List<String> = emptyList(),
    /** kind=provider 时: 注册表里的 provider 名。 */
    val provider: String = "",
    /** provider 执行时机: "before" / "after"; 空则 provider 默认 after, block 默认 before。 */
    val phase: String = "",
    /** provider 参数 —— 规则表带下来的数据(类名/映射表等), 让 provider 不必硬编码。 */
    val args: List<String> = emptyList(),
) {
    val isBlock: Boolean get() = kind == KIND_BLOCK
    val isProvider: Boolean get() = kind == KIND_PROVIDER
    val isConditional: Boolean get() = condArg >= 0
    /** 实际执行时机。 */
    val runAfter: Boolean get() = if (phase.isNotEmpty()) phase == "after" else isProvider

    fun encode(): String = listOf(
        kind,
        ret ?: "",
        condArg.toString(),
        condOp,
        condValues.joinToString(VS.toString()),
        provider,
        phase,
        args.joinToString(VS.toString()),
    ).joinToString(FS.toString())

    companion object {
        const val KIND_BLOCK = "block"
        const val KIND_PASS = "pass"
        const val KIND_PROVIDER = "provider"
        private const val FS = '\u0003'
        private const val VS = '\u0004'

        /** 无条件阻断, 结果按返回类型自适应。 */
        val BLOCK = ActionSpec()

        /** 无条件阻断, 返回指定字面量。 */
        fun block(ret: String?): ActionSpec = ActionSpec(ret = ret)

        /** 仅观测, 不拦。 */
        val PASS = ActionSpec(kind = KIND_PASS)

        fun decode(s: String): ActionSpec {
            if (!s.contains(FS)) {
                return when (s) {
                    "PASS" -> PASS
                    "BLOCK_VOID" -> ActionSpec(ret = "null")
                    else -> BLOCK
                }
            }
            val f = s.split(FS)
            return ActionSpec(
                kind = f.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: KIND_BLOCK,
                ret = f.getOrNull(1)?.takeIf { it.isNotEmpty() },
                condArg = f.getOrNull(2)?.toIntOrNull() ?: -1,
                condOp = f.getOrNull(3) ?: "",
                condValues = f.getOrNull(4)?.split(VS)?.filter { it.isNotEmpty() } ?: emptyList(),
                provider = f.getOrNull(5) ?: "",
                phase = f.getOrNull(6) ?: "",
                args = f.getOrNull(7)?.split(VS)?.filter { it.isNotEmpty() } ?: emptyList(),
            )
        }
    }
}

/** 一条 hook 目标 —— 后端与前端之间的唯一数据契约。 */
data class HookSpec(
    val ruleId: String,
    val className: String,
    val methodName: String,
    val paramTypes: List<String> = emptyList(),
    val returnType: String = "",
    val action: ActionSpec = ActionSpec.BLOCK,
) {
    fun encode(): String = listOf(
        ruleId, className, methodName,
        paramTypes.joinToString(","), returnType, action.encode(),
    ).joinToString("|")

    companion object {
        fun decode(s: String): HookSpec? {
            val p = s.split("|")
            if (p.size < 6) return null
            return HookSpec(
                ruleId = p[0],
                className = p[1],
                methodName = p[2],
                paramTypes = p[3].split(",").filter { it.isNotEmpty() },
                returnType = p[4],
                action = ActionSpec.decode(p[5]),
            )
        }
    }
}

/**
 * 后端解析器。前端只依赖这个接口，不关心后端是硬编码表、DexKit 还是 native。
 * 实现可替换 = 前后端可独立演进。
 */
interface TargetResolver {
    fun resolve(packageName: String, classLoader: ClassLoader, apkPath: String?): List<HookSpec>
}

/** resolver 模式开关（Settings.Global / SP 持久化）。 */
object ResolverMode {
    const val PREF = "resolver_mode"
    const val STATIC = "static"
    const val NATIVE = "native"
    const val DEXKIT = "dexkit"

    /** 默认模式: DexKit 锚点发现(带类名直连兜底)。 */
    const val DEFAULT = DEXKIT
}
