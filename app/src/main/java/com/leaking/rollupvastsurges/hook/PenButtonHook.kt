package com.leaking.rollupvastsurges.hook

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import com.leaking.rollupvastsurges.compat.*
import com.leaking.rollupvastsurges.compat.XposedHelpers.findAndHookMethod

/**
 * 笔按键重映射 — 在 initZygote 中执行
 * Hook 全局 View.dispatchTouchEvent，拦截触控笔按键
 * 支持: 橡皮擦切换 / 鼠标左键 / 菜单(KEYCODE_MENU) / 返回(KEYCODE_BACK) / 自定义键码
 */
object PenButtonHook {
    fun setup() {
        val action = HookEngine.readStr("pen_button_action", "none")
        if (action == "none") return
        try {
            findAndHookMethod(View::class.java, "dispatchTouchEvent",
                MotionEvent::class.java, object : XC_MethodHook() {
                    var eraserMode = false
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        try {
                            val event = param.args[0] as MotionEvent
                            if (event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) return
                            val bs = event.buttonState
                            val act = event.action and MotionEvent.ACTION_MASK
                            val btnPressed = (bs and 2) != 0 && act == MotionEvent.ACTION_DOWN
                            if (!btnPressed) {
                                if (action == "eraser" && eraserMode) {
                                    try {
                                        val m = MotionEvent::class.java.getMethod("setButtonState", Int::class.javaPrimitiveType)
                                        m.invoke(event, bs or 64)
                                    } catch (_: Throwable) {}
                                }
                                return
                            }
                            param.result = true
                            when (action) {
                                "eraser" -> { eraserMode = !eraserMode; XposedBridge.log("[MDM] Pen eraser toggle: $eraserMode") }
                                "mouse" -> doMouseClick()
                                "menu" -> doPenAction(82)
                                "back" -> doPenAction(4)
                                "custom" -> {
                                    val kc = HookEngine.readStr("pen_button_custom_key", "82").toIntOrNull() ?: 82
                                    doPenAction(kc)
                                }
                            }
                        } catch (e: Throwable) { XposedBridge.log("[MDM] pen remap error: ${e.message}") }
                    }
                })
        } catch (_: Throwable) {}
    }

    private fun doMouseClick() {
        try {
            val imm = Class.forName("android.hardware.input.InputManager")
            val getInst = imm.getMethod("getInstance")
            val im = getInst.invoke(null)
            val injectMethod = imm.getMethod("injectInputEvent",
                android.view.InputEvent::class.java, Int::class.javaPrimitiveType)
            val now = SystemClock.uptimeMillis()
            val dm = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 0f, 0f, 0)
            val setBs = MotionEvent::class.java.getMethod("setButtonState", Int::class.javaPrimitiveType)
            setBs.invoke(dm, MotionEvent.BUTTON_PRIMARY)
            injectMethod.invoke(im, dm, 0)
            dm.recycle()
            val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, 0f, 0f, 0)
            setBs.invoke(up, MotionEvent.BUTTON_PRIMARY)
            injectMethod.invoke(im, up, 0)
            up.recycle()
        } catch (_: Throwable) {}
    }

    private fun doPenAction(keyCode: Int) {
        try {
            val imm = Class.forName("android.hardware.input.InputManager")
            val getInst = imm.getMethod("getInstance")
            val im = getInst.invoke(null)
            val injectMethod = imm.getMethod("injectInputEvent",
                android.view.InputEvent::class.java, Int::class.javaPrimitiveType)
            val down = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                KeyEvent.ACTION_DOWN, keyCode, 0)
            injectMethod.invoke(im, down, 0)
            val up = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
                KeyEvent.ACTION_UP, keyCode, 0)
            injectMethod.invoke(im, up, 0)
        } catch (_: Throwable) {}
    }
}
