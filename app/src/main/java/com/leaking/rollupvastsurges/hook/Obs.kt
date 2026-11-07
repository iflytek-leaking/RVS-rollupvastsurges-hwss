package com.leaking.rollupvastsurges.hook

/**
 * 敏感字符串的编译期混淆工具。
 * 使用 UTF-8 字节流 XOR(0x5A) + Base64 编码存储，运行时解码，防反编译后静态搜索。
 *
 * 注意: 这不是密码学安全方案，只是阻止 jadx/apktool 直接 grep 到明文。
 * 真正的保密需在 native 层或服务端。
 */
object Obs {

    /** 把明文字符串编码为混淆形式（离线生成用） */
    fun encode(plain: String): String {
        val bytes = plain.toByteArray(Charsets.UTF_8)
        val xored = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor 0x5A).toByte() }
        return android.util.Base64.encodeToString(xored, android.util.Base64.NO_WRAP)
    }

    /** 运行时解码混淆字符串 */
    fun decode(encoded: String): String {
        return try {
            val bytes = android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP)
            val xored = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor 0x5A).toByte() }
            String(xored, Charsets.UTF_8)
        } catch (_: Throwable) { "" }
    }
}
