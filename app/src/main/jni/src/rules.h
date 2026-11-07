/*
 * rules.h — 后端(native) 规则表接口
 *
 * 规则表以异或加密形式编译进 libpenhook.so（见 rules.c，由 tools/rulecheck/gen_rules.py 生成）。
 * Java 前端通过 JNI nativeGetRules() 取回明文，反编译 classes.dex 看不到目标类名/锚点。
 *
 * 规则记录格式（记录间以 \n 分隔，字段以 \x1F 分隔）:
 *   ruleId \x1F classes(csv) \x1F anchors(csv) \x1F action \x1F packages(csv)
 */
#ifndef MDM_RULES_H
#define MDM_RULES_H

#ifdef __cplusplus
extern "C" {
#endif

/* 解密规则表写入 out（含 NUL 终止）。成功返回字节数(不含 NUL)，cap 不足返回 -1。 */
int mdm_get_rules(char *out, int cap);

#ifdef __cplusplus
}
#endif

#endif /* MDM_RULES_H */
