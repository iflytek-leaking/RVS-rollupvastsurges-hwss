package com.leaking.rollupvastsurges.resolver

import java.util.concurrent.ConcurrentHashMap

/**
 * 记录哪些包已由后端规则表 resolver 成功接管。
 * MDMHook 据此跳过 legacy 硬编码组循环 —— 避免 resolver 与 legacy 双重 hook。
 * resolver 失败/0 命中时不标记 → legacy 路径照常兜底。
 */
object ResolverState {
    private val handled = ConcurrentHashMap.newKeySet<String>()

    fun markHandled(pkg: String) { handled.add(pkg) }

    fun isHandled(pkg: String): Boolean = handled.contains(pkg)
}
