package com.leaking.rollupvastsurges.compat;

/**
 * legacy 同形 MethodHookParam。
 *
 * 为什么用 Java 而不是 Kotlin:
 * legacy 的 thisObject / args 是 Java 平台类型(Any!)，Kotlin 里声明 Any? 会让
 * 调用点 `param.thisObject::class.java` 报 "Expression in class literal has
 * nullable type"。Java 字段天然是平台类型，保证 4700 行 hook 代码零改动。
 */
public final class MethodHookParam {
    public Object thisObject;
    public Object[] args;

    /** beforeHookedMethod 里置位 → 跳过原方法直接返回 result。 */
    public boolean returnEarly;

    /** setThrowable 的等价物；置位后抛给调用方。 */
    public Throwable throwable;

    private Object result;

    MethodHookParam(Object thisObject, Object[] args) {
        this.thisObject = thisObject;
        this.args = args;
    }

    /** Kotlin 合成属性 param.result 的 getter。 */
    public Object getResult() {
        return result;
    }

    /** 复刻 legacy 语义：写 result == setResult() → returnEarly=true。 */
    public void setResult(Object result) {
        this.result = result;
        this.returnEarly = true;
    }

    /** 内部：after 阶段回填 proceed 的结果，不触发 returnEarly。 */
    void acceptResult(Object value) {
        this.result = value;
    }
}
