package com.leaking.rollupvastsurges.hook

import com.leaking.rollupvastsurges.compat.LoadPackageParam
import com.leaking.rollupvastsurges.compat.XposedHelpers

/**
 * 每个 Hook 子类对应一个目标进程。
 * - targetPackages: 绑定哪些包名
 * - prefKey: SettingsActivity 中 HookItem 的 key
 * - register(lpparam): 外部入口，存储 lpparam 后调用 onRegister()
 *
 * 子类在文件末尾的 init 块中自注册：
 *   init { HookRegistry.register(this) }
 */
abstract class BaseHook {
    abstract val targetPackages: Set<String>
    abstract val prefKey: String

    protected lateinit var lpparam: LoadPackageParam
        private set

    fun register(lpparam: LoadPackageParam) {
        this.lpparam = lpparam
        onRegister()
    }

    protected abstract fun onRegister()

    fun findClass(name: String) = XposedHelpers.findClass(name, lpparam.classLoader)
}

/**
 * 包名 → BaseHook 的分发表。
 * MDMModule.dispatch 中查找:
 *   HookRegistry.forPackage(pkg)?.register(lpparam)
 */
object HookRegistry {
    private val map = mutableMapOf<String, MutableList<BaseHook>>()
    fun register(hook: BaseHook) {
        hook.targetPackages.forEach { map.getOrPut(it) { mutableListOf() }.add(hook) }
    }
    fun forPackage(pkg: String): List<BaseHook> = map[pkg] ?: emptyList()
}
