package io.laplace.sprite.hotreload;

/**
 * JVM 类型（15.2 重载能力自检矩阵）：它决定方法体之外的结构改动能否热生效。
 */
public enum JvmKind {

    /** 标准 HotSpot：只支持改方法体。 */
    HOTSPOT("HotSpot"),
    /** DCEVM：增强类重定义，可增删方法 / 字段。 */
    DCEVM("DCEVM"),
    /** JetBrains Runtime：同样支持增强类重定义。 */
    JETBRAINS_RUNTIME("JetBrains Runtime"),
    /** 探测不出类型：一律按需要重启处理。 */
    UNKNOWN("未知 JVM");

    private final String displayName;

    JvmKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** 是否支持 -XX:+AllowEnhancedClassRedefinition 的增强类重定义。 */
    public boolean supportsEnhancedRedefinition() {
        return this == DCEVM || this == JETBRAINS_RUNTIME;
    }
}
