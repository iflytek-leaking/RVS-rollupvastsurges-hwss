/*
 * libmdmrules — 后端规则表独立共享库
 *
 * 与 libpenhook.so 分离: penhook 负责笔按键 native hook, 本库只负责
 * 持有并解密规则表。职责分开, 互不牵连。
 *
 * 规则表以异或加密形式编译进本库(rules.c, 由 tools/rulecheck/gen_rules.py 生成),
 * Java 前端通过 JNI nativeGetRules() 取回明文 —— 反编译 classes.dex 看不到目标。
 */
#include <jni.h>
#include "rules.h"

JNIEXPORT jstring JNICALL
Java_com_leaking_rollupvastsurges_backend_NativeRules_nativeGetRules(JNIEnv *env, jclass clazz, jstring romType) {
    (void) clazz;
    (void) romType;   /* 预留给后续按 ROM 版本选择不同规则表 */
    static char buf[16384];
    int n = mdm_get_rules(buf, (int) sizeof(buf));
    if (n <= 0) {
        return (*env)->NewStringUTF(env, "");
    }
    return (*env)->NewStringUTF(env, buf);
}
