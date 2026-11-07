package com.leaking.rollupvastsurges.hook

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process

/**
 * 跨进程 hook 状态共享 ContentProvider。
 *
 * 安全策略：
 *   - 写入（METHOD_PUT）：仅允许本应用 UID、system UID、root 调用
 *   - 读取（METHOD_GET/GET_ALL/HAS/query）：允许任何调用者（hook 状态非敏感信息）
 *
 * 用法：
 *   写入: contentResolver.call(hookStatusUri, "put", "hookKey=1", null)
 *   读取: contentResolver.call(hookStatusUri, "get", "hookKey", null) → Bundle
 */
class HookStatusProvider : ContentProvider() {

    companion object {
        const val AUTHORITY = "com.leaking.rollupvastsurges.hookstatus"
        const val METHOD_PUT = "put"       // arg = "key=value"
        const val METHOD_GET = "get"       // arg = "key"
        const val METHOD_GET_ALL = "getAll"
        const val METHOD_HAS = "has"       // arg = "key", 返回 Bundle{value: bool}

        val CONTENT_URI = Uri.parse("content://$AUTHORITY/")

        // 内存缓存 + 按 key 持久化到 SP
        private val cache = mutableMapOf<String, String>()
        private const val PREFS_NAME = "rollupvastsurges_hook_status"
    }

    override fun onCreate(): Boolean {
        // 从 SP 恢复持久化状态
        try {
            val sp = context?.getSharedPreferences(PREFS_NAME, 0) ?: return true
            cache.putAll(sp.all.map { (k, v) -> k to (v?.toString() ?: "0") })
        } catch (_: Exception) {}
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val callingUid = Binder.getCallingUid()

        // 写入操作：仅允许本应用 UID、system UID、root
        if (method == METHOD_PUT) {
            if (callingUid != Process.myUid() && callingUid != Process.SYSTEM_UID && callingUid != 0) {
                return null
            }
        }

        return when (method) {
            METHOD_PUT -> {
                if (arg != null) {
                    val eq = arg.indexOf('=')
                    if (eq > 0) {
                        val key = arg.substring(0, eq)
                        val value = arg.substring(eq + 1)
                        cache[key] = value
                        try {
                            context?.getSharedPreferences(PREFS_NAME, 0)
                                ?.edit()?.putString(key, value)?.apply()
                        } catch (_: Exception) {}
                    }
                }
                null
            }
            METHOD_GET -> {
                val v = if (arg != null) cache[arg] else null
                if (v != null) Bundle().apply { putString("value", v) } else null
            }
            METHOD_HAS -> {
                val v = if (arg != null) cache[arg] else null
                Bundle().apply { putBoolean("value", v != null && v == "1") }
            }
            METHOD_GET_ALL -> {
                Bundle().apply {
                    cache.forEach { (k, v) -> putString(k, v) }
                }
            }
            else -> null
        }
    }

    // ── 以下为 ContentProvider 必须实现的方法（query 也可用）──

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? {
        val callingUid = Binder.getCallingUid()
        // 读取可不限制 UID，但查询全部时拒绝非授权调用者
        if (selection == null && callingUid != Process.myUid() && callingUid != Process.SYSTEM_UID && callingUid != 0) {
            return null
        }
        return if (selection == null) {
            // 返回全部
            val cols = arrayOf("_id", "key", "value", "updated_at")
            val cursor = MatrixCursor(cols)
            cache.entries.forEachIndexed { i, (k, v) ->
                cursor.addRow(arrayOf<Any?>(i.toLong(), k, v, System.currentTimeMillis()))
            }
            cursor
        } else {
            // 按 key 查询
            val v = cache[selection]
            if (v != null) {
                MatrixCursor(arrayOf("_id", "key", "value", "updated_at")).apply {
                    addRow(arrayOf<Any?>(0L, selection, v, System.currentTimeMillis()))
                }
            } else null
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.rollupvastsurges.hookstatus"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
