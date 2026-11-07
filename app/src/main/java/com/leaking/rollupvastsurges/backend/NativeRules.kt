package com.leaking.rollupvastsurges.backend

import com.leaking.rollupvastsurges.contract.ActionSpec
import com.leaking.rollupvastsurges.contract.RuleRecord

/**
 * 后端规则表入口。规则明文只存在于 libmdmrules.so 的加密数组里，
 * 运行时经 JNI 解密后交给前端。反编译 classes.dex 看不到目标类名/锚点。
 */
object NativeRules {
    private const val FIELD = '\u001F'
    private const val LIST = '\u0002'
    private const val RECORD = '\n'

    @Volatile private var libReady = false

    external fun nativeGetRules(romType: String): String

    private fun ensureLoaded() {
        if (libReady) return
        synchronized(this) {
            if (libReady) return
            System.loadLibrary("mdmrules")
            libReady = true
        }
    }

    /** 取回并解析规则表。库未加载/解码失败一律返回空表，前端自动降级。 */
    fun load(romType: String = ""): List<RuleRecord> {
        val blob = try {
            ensureLoaded()
            nativeGetRules(romType)
        } catch (t: Throwable) {
            return emptyList()
        }
        if (blob.isEmpty()) return emptyList()
        return blob.split(RECORD).filter { it.isNotEmpty() }.mapNotNull(::parse)
    }

    private fun parse(line: String): RuleRecord? {
        val f = line.split(FIELD)
        if (f.size < 5) return null
        fun list(i: Int) = f.getOrNull(i)?.split(LIST)?.filter { it.isNotEmpty() } ?: emptyList()
        return RuleRecord(
            ruleId = f[0],
            classes = list(1),
            anchors = list(2),
            action = ActionSpec.decode(f[3]),
            packages = list(4),
            methods = list(5),
            params = list(6),
            scope = list(7),
        )
    }
}
