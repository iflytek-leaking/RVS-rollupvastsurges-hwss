package com.leaking.rollupvastsurges.hook

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/**
 * 自定义控制卡牌 — 注入到 TyeLauncher 桌面
 *
 * 不继承 BaseLauncherHolder，通过 Xposed 反射在运行时绑定。
 * Hook 点：BizCardMeta.createInstance() 拦截后通过反射实例化。
 */
class CustomControlCard(activity: Activity, metaInfo: Any) {

    private val TAG = "CustomControlCard"
    private val PREFS_NAME = "rollupvastsurges_prefs"

    // 通过反射从 metaInfo 读取
    private val cardName: String
    private val pageId: Long

    // ── UI 元素 ──
    private lateinit var statusText: TextView
    private lateinit var refreshRateGroup: LinearLayout
    private lateinit var toggleContainer: LinearLayout

    // 运行时通过反射设置的 View
    var mRootView: ViewGroup? = null
        private set

    init {
        // 通过反射读取 mCardName 和 mPageId
        val nameField = try { metaInfo::class.java.getDeclaredField("mCardName").also { it.isAccessible = true } } catch (_: Exception) { null }
        cardName = (nameField?.get(metaInfo) as? String) ?: "control_center"
        val idField = try { metaInfo::class.java.getDeclaredField("mPageId").also { it.isAccessible = true } } catch (_: Exception) { null }
        pageId = (idField?.get(metaInfo) as? Long) ?: -1L
    }

    /**
     * 构建 UI（对应 BaseLauncherHolder.initView）
     */
    fun initView(context: Context, rootView: ViewGroup) {
        val scrollView = ScrollView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(24, 24, 24, 24)
        }

        // ── 标题 ──
        container.addView(TextView(context).apply {
            text = "\u2699 \u5377\u5de8\u6f9c \u00b7 \u63a7\u5236\u4e2d\u5fc3"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 16)
        })

        // ── 状态概览 ──
        statusText = TextView(context).apply {
            textSize = 14f
            setTextColor(Color.argb(200, 255, 255, 255))
            setPadding(0, 0, 0, 24)
        }
        container.addView(statusText)

        // ── 分割线 ──
        container.addView(createDivider(context))

        // ── 开关组标题 ──
        container.addView(TextView(context).apply {
            text = "\u5feb\u901f\u5f00\u5173"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 8)
        })

        // ── 开关列表 ──
        toggleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        container.addView(toggleContainer)

        // ── 分割线 ──
        container.addView(createDivider(context))

        // ── 刷新率控制 ──
        container.addView(TextView(context).apply {
            text = "\u5c4f\u5e55\u5237\u65b0\u7387"
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 8)
        })

        refreshRateGroup = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(refreshRateGroup)

        // ── 提示 ──
        container.addView(TextView(context).apply {
            text = "\n[!] \u81ea\u5b9a\u4e49\u5361\u724c (ExternalFilesDir/tyelauncher_custom_cards.json)"
            textSize = 12f
            setTextColor(Color.argb(128, 255, 255, 255))
        })

        scrollView.addView(container)
        rootView.addView(scrollView)
        mRootView = rootView

        buildToggles(context)
        buildRefreshRateButtons(context)
        refreshStatus(context)
    }

    fun onActivityResume(context: Context, currentScreen: Int) {
        refreshStatus(context)
    }

    fun onActivityPause() {}

    fun onDestroy() {}

    private fun buildToggles(context: Context) {
        val toggles = listOf(
            Triple("func_app_control", "\u5e94\u7528\u51bb\u7ed3", "\u7981\u6b62 MDM \u51bb\u7ed3\u5e94\u7528"),
            Triple("func_network_control", "\u8054\u7f51\u7ba1\u63a7", "\u7981\u6b62 MDM URL \u8fc7\u6ee4"),
            Triple("func_usb", "USB \u7981\u7528", "\u7981\u6b62\u8fdc\u7a0b\u7981\u7528 USB"),
            Triple("func_adb_keep", "ADB \u4fdd\u6301", "\u9632\u6b62\u81ea\u52a8\u5173\u95ed ADB"),
            Triple("func_stats_report", "\u7edf\u8ba1\u4e0a\u62a5", "\u7981\u6b62\u4f7f\u7528\u65f6\u957f\u4e0a\u62a5"),
            Triple("tyelauncher_follow_system_wallpaper", "\u5899\u7eb8\u8ddf\u968f\u7cfb\u7edf", "\u963b\u6b62\u573a\u666f\u80cc\u666f\u56fe\u52a0\u8f7d")
        )
        for ((prefKey, label, _) in toggles) {
            val row = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, 8, 0, 8)
                gravity = Gravity.CENTER_VERTICAL
            }

            val textCol = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL
            }
            textCol.addView(TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(Color.WHITE)
            })
            textCol.addView(TextView(context).apply {
                text = prefKey
                textSize = 12f
                setTextColor(Color.argb(180, 255, 255, 255))
            })
            row.addView(textCol)

            val sp = context.getSharedPreferences(PREFS_NAME, 0)
            val sw = Switch(context).apply {
                isChecked = sp.getBoolean(prefKey, prefKey in listOf(
                    "func_app_control", "func_network_control", "func_usb", "func_stats_report"
                ))
                setOnCheckedChangeListener { _, isChecked ->
                    sp.edit().putBoolean(prefKey, isChecked).apply()
                    refreshStatus(context)
                }
            }
            row.addView(sw)
            toggleContainer.addView(row)
        }
    }

    private fun buildRefreshRateButtons(context: Context) {
        val rates = listOf(60, 90, 120)
        for (hz in rates) {
            val btn = Button(context).apply {
                text = "${hz}Hz"
                layoutParams = LinearLayout.LayoutParams(0, 120, 1f).apply {
                    setMargins(4, 4, 4, 4)
                }
                setOnClickListener { setRefreshRate(hz); refreshStatus(context) }
                val current = getCurrentRefreshRate(context)
                setBackgroundColor(
                    if (hz == current) Color.argb(100, 100, 200, 255)
                    else Color.argb(40, 255, 255, 255)
                )
                setTextColor(Color.WHITE)
            }
            refreshRateGroup.addView(btn)
        }
    }

    private fun refreshStatus(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val active = listOf(
            "func_app_control" to "\u5e94\u7528\u51bb\u7ed3",
            "func_network_control" to "\u8054\u7f51\u7ba1\u63a7",
            "func_usb" to "USB",
            "func_stats_report" to "\u4e0a\u62a5",
            "func_adb_keep" to "ADB"
        ).filter { (key, _) -> prefs.getBoolean(key, key in listOf(
            "func_app_control", "func_network_control", "func_usb", "func_stats_report"
        )) }.map { it.second }

        val hz = getCurrentRefreshRate(context)
        statusText.text = "[Guard] \u62a4\u62a4: ${active.size}/5 \u00b7 \u5237\u65b0\u7387: ${hz}Hz"
    }

    private fun getCurrentRefreshRate(context: Context): Int {
        return try {
            @Suppress("DEPRECATION")
            val display = (context.getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager)?.defaultDisplay
            display?.mode?.refreshRate?.toInt() ?: 60
        } catch (_: Exception) { 60 }
    }

    private fun setRefreshRate(hz: Int) {
        try {
            val spClass = Class.forName("android.os.SystemProperties")
            val set = spClass.getMethod("set", String::class.java, String::class.java)
            for (prop in listOf(
                "persist.vendor.hwc.refresh_rate",
                "persist.vendor.display.refresh_rate",
                "debug.egl.swapinterval"
            )) {
                try { set.invoke(null, prop, hz.toString()) } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun createDivider(context: Context): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 1
        ).apply { setMargins(0, 8, 0, 8) }
        setBackgroundColor(Color.argb(60, 255, 255, 255))
    }
}
