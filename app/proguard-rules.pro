# ── 卷巨澜 ProGuard Rules ──

# Xposed 入口 — 必须保留完整类名
-keep class com.leaking.rollupvastsurges.hook.MDMModule { *; }
# 顶层函数 (detectRomType 等) 编译后为 MDMModuleKt
-keep class com.leaking.rollupvastsurges.hook.MDMModuleKt { *; }

# libxposed 生命周期回调必须保留方法签名（框架按名字回调）
-keepclassmembers class com.leaking.rollupvastsurges.hook.MDMModule {
    public <init>();
    public void onModuleLoaded(...);
    public void onPackageLoaded(...);
    public void onPackageReady(...);
    public void onSystemServerStarting(...);
}

# JNI 导出 — 类名/方法名必须与 libpenhook.so 的符号完全一致
# (Java_com_leaking_rollupvastsurges_hook_NativeStatusKt_* / ..._backend_NativeRules_nativeGetRules)
-keep class com.leaking.rollupvastsurges.hook.NativeStatusKt { *; }
-keep class com.leaking.rollupvastsurges.backend.NativeRules { *; }

# DexKit — JNI 桥, R8 重命名/重打包会导致 native FindClass 失败
-keep class org.luckypray.dexkit.** { *; }
-keepclassmembers class org.luckypray.dexkit.** { *; }
-dontwarn org.luckypray.dexkit.**
-keep class com.google.flatbuffers.** { *; }
-keep class com.leaking.rollupvastsurges.hook.RomType { *; }

# Compose Activity — Manifest 注册, 系统通过反射启动
-keep class com.leaking.rollupvastsurges.ui.SettingsActivity { *; }
-keep class com.leaking.rollupvastsurges.ui.ThemeCardActivity { *; }

# 注: 不 keep hook.** 通配。HookRegistry 按实例注册(init { register(this) }),
# MDMHook/SystemHook 等均由 MDMModule 直接构造, 无按名反射加载, 全部允许混淆。
# IntegrityCheck/Cfo/Obs/HookEngine 被 R8 改名后, 反编译看不到 isIntact/
# fakeAntiDebugCheck 等自说明命名 — 这是混淆投入的关键收益。

# 反射调用的外部类(目标系统/厂商类) — 不混淆
-keep class android.app.mia.** { *; }
-keep class com.iflytek.** { *; }
-keep class android.iflytek.** { *; }
-keep class cn.com.microtrust.** { *; }

# Composable 函数签名 — 混淆会导致 Compose 崩溃
-keepclasseswithmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}

# Compose runtime — 防止 R8 混淆内部类
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 控制流混淆工具 — 保留方法体结构(防 R8 内联/优化消除不透明谓词),
# 但允许类名/方法名混淆(allowobfuscation), 避免暴露 Cfo/Obs 自说明命名。
-keep,allowobfuscation class com.leaking.rollupvastsurges.hook.Cfo { *; }
-keep,allowobfuscation class com.leaking.rollupvastsurges.hook.Obs { *; }

# 通用的
-dontwarn java.lang.invoke.**
-dontwarn org.jetbrains.annotations.**
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# 其他代码允许混淆 — 方法名/类名都可以变
-allowaccessmodification
-repackageclasses ''
