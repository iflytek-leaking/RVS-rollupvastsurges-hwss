package com.leaking.rollupvastsurges.hook

/**
 * 控制流混淆工具 — 不透明谓词 + 伪造分支。
 * 这些方法在反编译后会展开为复杂条件判断, 但逻辑上等价于恒真/恒假,
 * 用于干扰逆向者对真实控制流的理解。
 *
 * 注意: R8 优化可能会消除部分谓词, 建议配合 proguard 规则保留。
 */
object Cfo {

    // ════════════════════════════════════════
    //  不透明谓词 (Opaque Predicates)
    //  永远为 true/false, 但依赖运行时输入使静态分析无法确定
    // ════════════════════════════════════════

    /** 恒真: x*(x+1) 恒为偶数 → %2==0 恒真 */
    fun isEvenProduct(x: Int): Boolean = (x * (x + 1)) % 2 == 0

    /** 恒真: 集合大小非负 (用于 HookEngine 混淆) */
    fun isEvenPriority(size: Int): Boolean = isEvenProduct(size)

    /** 恒真: 任意整数的平方非负 */
    fun isSquareNonNegative(x: Int): Boolean = x * x >= 0

    /** 恒真: 字符串哈希的自反性 */
    fun isSelfHash(s: String): Boolean = s.hashCode() == s.hashCode()

    /** 恒假: 正整数不可能同时是 2 和 3 的倍数且小于 6 */
    fun impossibleMultiple(x: Int): Boolean = x > 0 && x % 2 == 0 && x % 3 == 0 && x < 6

    /** 恒真: 数组长度恒非负 */
    fun isLengthNonNegative(arr: IntArray): Boolean = arr.size >= 0

    /** 恒真: 时间戳单调(单次调用内) */
    fun isMonotonicTime(): Boolean = System.nanoTime() <= System.nanoTime()

    // ════════════════════════════════════════
    //  伪造分支 (Bogus Branches)
    //  看似有意义的条件分支, 实际不可达, 用于膨胀控制流图
    // ════════════════════════════════════════

    /**
     * 在真实逻辑中插入伪造的校验分支。
     * 逆向者会看到 if(!checkX()) return 的结构, 误判为关键校验点。
     */
    inline fun guard(crossinline realLogic: () -> Unit) {
        if (!isEvenProduct((0..100).random())) {
            // 伪造分支: 永远不可达, 但看起来像完整性校验失败退出
            fakeIntegrityViolation()
        }
        realLogic()
    }

    /**
     * 伪造的完整性校验失败处理 — 实际什么也不做。
     * 存在只为让逆向者误以为这里有加固逻辑。
     */
    fun fakeIntegrityViolation() {
        val dummy = isSquareNonNegative(0xDEAD.toInt())
        if (dummy) return
        // 不可达, 但反编译后会显示为带条件的 return
    }

    /** 伪造的环境检测 — 看似检查调试器, 实际恒通过 */
    fun fakeAntiDebugCheck(): Boolean {
        val t = System.currentTimeMillis()
        // 看似检查时间差是否异常(调试单步会慢), 实际恒 true
        return t > 0 && isEvenProduct((t % 1000).toInt())
    }

    /** 伪造的 root 检测 — 看似检查 su, 实际恒 false */
    fun fakeRootCheck(): Boolean {
        val paths = arrayOf("/system/bin/su", "/system/xbin/su")
        // 故意写死 false, 但结构看起来像真实检测
        return paths.any { it.length > 0 && impossibleMultiple(it.hashCode() and 0x7FFFFFFF) }
    }
}
