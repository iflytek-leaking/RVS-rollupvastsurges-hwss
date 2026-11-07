package com.leaking.rollupvastsurges.hook

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * 运行时动态接口代理 — 让任意对象实现目标 APK 的接口。
 * 用法:
 *   val proxy = InterfaceProxy.wrap(card, "com.iflytek.aistudy.launchercard.ILauncherCardPageHolder", lpparam.classLoader)
 *
 * 基于 TyeLauncher DEX (classes2.dex) 验证接口:
 *   ILauncherCardPageHolder 共 18 个方法 (参见 inline docs)
 */
object InterfaceProxy {

    /**
     * 将 [target] 包装成实现 [interfaceName] 的 Proxy 实例。
     * @param interfaceName 全限定接口名 (如 com.iflytek.aistudy.launchercard.ILauncherCardPageHolder)
     * @param classLoader 目标 APK 的 ClassLoader (lpparam.classLoader)
     */
    fun wrap(target: Any, interfaceName: String, classLoader: ClassLoader): Any {
        val interfaceClass = try {
            Class.forName(interfaceName, false, classLoader)
        } catch (_: Exception) {
            return target
        }
        if (!interfaceClass.isInterface) return target
        val handler = MethodDispatchHandler(target)
        return Proxy.newProxyInstance(classLoader, arrayOf(interfaceClass), handler)
    }

    /**
     * InvocationHandler — 将接口方法路由到 target 的匹配方法。
     *
     * 匹配规则 (fallthrough priority):
     * 1. Object 方法 (toString/hashCode/equals) → 直接转发
     * 2. 方法名 + 参数数量完全匹配 → 反射调用 target 方法
     *    (参数类型不重要: target 的方法可以接收 Any/Object)
     * 3. 无匹配 → 返回类型安全的默认值 (0/false/null)
     */
    private class MethodDispatchHandler(private val target: Any) : InvocationHandler {

        private val methodCache = mutableMapOf<String, Method?>()

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            val name = method.name

            // Object 方法
            when (name) {
                "toString" -> return target.toString()
                "hashCode" -> return target.hashCode()
                "equals" -> {
                    val other = args?.getOrNull(0) ?: return false
                    if (Proxy.isProxyClass(other::class.java)) {
                        val h = Proxy.getInvocationHandler(other)
                        if (h is MethodDispatchHandler) return target === h.target
                    }
                    return target === other
                }
            }

            // 查找 target 上的匹配方法
            val targetMethod = findTargetMethod(name, method.parameterTypes.size)
            if (targetMethod != null) {
                return try {
                    targetMethod.invoke(target, *(args ?: emptyArray()))
                } catch (_: Exception) { null }
            }

            // 默认返回值
            return method.returnType.defaultValue()
        }

        private fun findTargetMethod(name: String, paramCount: Int): Method? {
            val key = "$name#$paramCount"
            methodCache[key]?.let { return it }
            val result = target::class.java.methods.firstOrNull { m ->
                m.name == name && m.parameterCount == paramCount
            }
            methodCache[key] = result
            return result
        }

        private fun Class<*>.defaultValue(): Any? = when (this) {
            Void.TYPE -> null
            java.lang.Boolean.TYPE -> false
            java.lang.Byte.TYPE -> 0.toByte()
            java.lang.Short.TYPE -> 0.toShort()
            java.lang.Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            java.lang.Character.TYPE -> '\u0000'
            else -> null
        }
    }
}
