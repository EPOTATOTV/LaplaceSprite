package io.laplace.sprite.hotreload;

/**
 * 改动类型（15.2）：热重载轨按这个粒度判断能否热生效。
 */
public enum ChangeKind {

    /** 方法体内部改动。 */
    METHOD_BODY("方法体"),
    /** 方法签名改动（增删改方法）。 */
    METHOD_SIGNATURE("方法签名"),
    /** 字段增删改。 */
    FIELD("字段"),
    /** Mixin 织入内容。 */
    MIXIN("Mixin"),
    /** 客户端资源（assets/）。 */
    RESOURCE_ASSETS("客户端资源 assets/"),
    /** 数据包（data/）。 */
    RESOURCE_DATA("数据包 data/"),
    /** mods 目录。 */
    MODS_DIR("mods 目录"),
    /** 插件 jar。 */
    PLUGIN_JAR("插件 jar");

    private final String displayName;

    ChangeKind(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
