package com.leaking.rollupvastsurges.contract

/**
 * 一条后端规则。前端只知道"要 hook 哪一类东西"，
 * 不含任何 iFLYTEK 类名/锚点字符串（那些在 libmdmrules.so 的加密表里）。
 *
 * 线上格式（native -> Java）:
 *   ruleId US classes STX ... US anchors STX ... US action US packages STX ... US methods STX ...
 * 列表用 STX(U+0002) 而非逗号: 锚点本身可能含逗号（如 "xxx, setResult:"）。
 *
 * [methods] 为空 -> 用 [com.leaking.rollupvastsurges.resolver.MethodFilter] 的启发式过滤;
 * 非空 -> 精确匹配这些方法名(SystemHook 这类本来就是显式方法名表的)。
 */
enum class RuleKind { CLASS_LIST, ANCHOR }

data class RuleRecord(
    val ruleId: String,
    val classes: List<String> = emptyList(),
    val anchors: List<String> = emptyList(),
    val action: ActionSpec = ActionSpec.BLOCK,
    val packages: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
    /**
     * 与 [methods] 同序的参数签名约束(VS 分隔)。
     * 空 -> 不做签名过滤; 条目 "*" -> 任意; 条目 "long,long" -> 精确匹配。
     * 用于避免同名重载被全部 hook(legacy 是按签名精确匹配的)。
     */
    val params: List<String> = emptyList(),
    /** 目标进程包名。空 -> 默认 MDM_PKGS。区别于 [packages](DexKit 搜索域)。 */
    val scope: List<String> = emptyList(),
) {
    /** 有锚点 -> 可用 DexKit 发现；否则只能靠类名直连。 */
    val kind: RuleKind get() = if (anchors.isEmpty()) RuleKind.CLASS_LIST else RuleKind.ANCHOR
}
