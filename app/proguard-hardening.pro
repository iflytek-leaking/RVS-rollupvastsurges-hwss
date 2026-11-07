# ── 卷巨澜 加固 R8 Rules ──
# 防逆向分析 + 防二次打包, 在 proguard-rules.pro 基础上加强。

# ── 资源混淆 ──
# 混淆 res/ 文件名和字符串资源内容, 阻止 apktool 直接还原语义
-adaptresourcefilenames
-adaptresourcefilecontents

# ── 混淆强化 ──
# 更激进的类名混淆（嵌套类也重打包到根包）
-repackageclasses ''
-flattenpackagehierarchy ''
# 允许访问修饰符修改（package-private → public 等）便于混淆
-allowaccessmodification

# ── 移除调试信息 ──
# 去掉 SourceFile/LineNumberTable, 反编译后无行号和原文件名
-renamesourcefileattribute ""
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# 移除 SourceDebugExtension
-dontnote java.lang.invoke.**

# ── 保留项继承 proguard-rules.pro ──
# Xposed 入口/反射目标类已在主规则中保留, 此处不重复
