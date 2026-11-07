package com.leaking.rollupvastsurges.compat

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Executable
import java.lang.reflect.Member

/**
 * ── legacy-compatible adapter over libxposed API 102 ──
 *
 * 把 libxposed 的 interceptor API 包装成"旧 Xposed API 形状"的内部接口，
 * 让 14 个 hook 文件几乎零改动完成迁移（只换 import）。
 *
 * 为什么:
 *  - 4700 行 hook 逻辑里有 73 个匿名 XC_MethodHook、54 处 hookMethod 是机械代码，
 *    逐个改写成 interceptor 风格只会引入大量人为错误。
 *  - 语义等价: legacy 的 MethodHookParam.result 是私有字段，Kotlin 通过
 *    getResult()/setResult() 合成属性；setResult 会置 returnEarly=true。
 *    本适配层的 result setter 精确复刻该行为。
 *
 * 合法性: 这些是本项目自己的 Kotlin 类，不是 de.robv.android.xposed。
 * API 102 禁止调用 legacy 框架 API，但自定义兼容层完全合法（底层只调 libxposed）。
 */

// MethodHookParam 用 Java 实现（见 MethodHookParam.java）:
// legacy 的 thisObject/args 是 Java 平台类型 Any!，Kotlin 的 Any? 会破坏调用点。

/** 与 legacy 同形的回调基类。 */
abstract class XC_MethodHook {
    open fun beforeHookedMethod(param: MethodHookParam) {}
    open fun afterHookedMethod(param: MethodHookParam) {}
}

/** legacy 形状的 lpparam，替代 XC_LoadPackage.LoadPackageParam。 */
class LoadPackageParam(
    val packageName: String,
    val classLoader: ClassLoader,
)

/** interceptor 适配: legacy before/after 语义 → libxposed Chain。 */
private class LegacyHooker(private val callback: XC_MethodHook) : XposedInterface.Hooker {
    override fun intercept(chain: XposedInterface.Chain): Any? {
        val param = MethodHookParam(chain.thisObject, chain.args.toTypedArray())

        callback.beforeHookedMethod(param)
        param.throwable?.let { throw it }
        if (param.returnEarly) return param.result

        // args 可能被 beforeHookedMethod 改写 → 用改写后的实参继续
        val proceeded = chain.proceed(param.args)
        param.acceptResult(proceeded)

        callback.afterHookedMethod(param)
        param.throwable?.let { throw it }
        return param.result
    }
}

/**
 * 前端 hook 注册入口。所有 hook 文件通过它装 hook。
 *
 * activeModule 由 MDMModule.onModuleLoaded 注入；注入前调用会退化为日志。
 */
object XposedBridge {
    const val TAG = "MDM"

    @Volatile
    var activeModule: XposedModule? = null

    @JvmStatic
    fun log(message: String) {
        val m = activeModule
        if (m != null) {
            try { m.log(Log.INFO, TAG, message); return } catch (_: Throwable) {}
        }
        Log.i(TAG, message)
    }

    @JvmStatic
    fun log(t: Throwable) {
        val m = activeModule
        if (m != null) {
            try { m.log(Log.ERROR, TAG, "hook exception", t); return } catch (_: Throwable) {}
        }
        Log.e(TAG, "hook exception", t)
    }

    /** 装一个方法 hook。失败不抛出（保持 best-effort，避免中断整批注册）。 */
    @JvmStatic
    fun hookMethod(member: Member, callback: XC_MethodHook) {
        val module = activeModule
        if (module == null) {
            Log.w(TAG, "hookMethod called before module attached: $member")
            return
        }
        val executable = member as? Executable
        if (executable == null) {
            Log.w(TAG, "not an Executable: $member")
            return
        }
        try {
            module.hook(executable).intercept(LegacyHooker(callback))
        } catch (t: Throwable) {
            Log.e(TAG, "hookMethod failed: $executable", t)
        }
    }

    /** hook 某个类（含父类）上所有同名方法。 */
    @JvmStatic
    fun hookAllMethods(clazz: Class<*>, methodName: String, callback: XC_MethodHook) {
        var c: Class<*>? = clazz
        while (c != null) {
            for (m in c.declaredMethods) {
                if (m.name == methodName) hookMethod(m, callback)
            }
            c = c.superclass
        }
    }
}

/** legacy XposedHelpers 的等价实现（纯反射，无框架依赖）。 */
object XposedHelpers {
    @JvmStatic
    fun findClass(name: String, classLoader: ClassLoader?): Class<*> =
        Class.forName(name, false, classLoader)

    @JvmStatic
    fun setIntField(obj: Any, fieldName: String, value: Int) {
        var c: Class<*>? = obj.javaClass
        while (c != null) {
            try {
                val f = c.getDeclaredField(fieldName)
                f.isAccessible = true
                f.setInt(obj, value)
                return
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        throw NoSuchFieldError(fieldName)
    }

    private fun findMethodExact(clazz: Class<*>, name: String, vararg types: Class<*>): java.lang.reflect.Method? {
        var c: Class<*>? = clazz
        while (c != null) {
            try {
                return c.getDeclaredMethod(name, *types)
            } catch (_: NoSuchMethodException) {
                c = c.superclass
            }
        }
        return null
    }

    /** findAndHookMethod(Class, String, Class..., callback) */
    @JvmStatic
    fun findAndHookMethod(
        clazz: Class<*>,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ) {
        val callback = parameterTypesAndCallback.lastOrNull() as? XC_MethodHook ?: return
        val types = parameterTypesAndCallback.dropLast(1).map { it as Class<*> }.toTypedArray()
        val method = findMethodExact(clazz, methodName, *types)
            ?: throw NoSuchMethodError("${clazz.name}.${methodName}${types.contentToString()}")
        XposedBridge.hookMethod(method, callback)
    }

    /** findAndHookMethod(String className, ClassLoader, String, Class..., callback) */
    @JvmStatic
    fun findAndHookMethod(
        className: String,
        classLoader: ClassLoader?,
        methodName: String,
        vararg parameterTypesAndCallback: Any?,
    ) {
        findAndHookMethod(findClass(className, classLoader), methodName, *parameterTypesAndCallback)
    }
}
