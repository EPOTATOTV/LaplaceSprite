package io.laplace.sprite.crashdiag;

/**
 * 崩溃分类（15.5 特征签名路由）。
 */
public enum CrashCategory {

    /** Mixin 注入失败 / 类访问冲突。 */
    MIXIN_CONFLICT("Mixin 与类冲突"),
    /** 依赖或版本矩阵不匹配。 */
    DEPENDENCY_MISMATCH("依赖版本不匹配"),
    /** JVM 原生崩溃（hs_err_pid），与业务异常区分。 */
    JVM_NATIVE("JVM 原生崩溃"),
    /** 堆内存不足。 */
    OUT_OF_MEMORY("内存不足"),
    /**
     * 资源包 / 数据包加载失败（15.8）。
     *
     * <p>插在 UNKNOWN 之前，兜底类别仍排最后；其余类别的相对顺序不变。</p>
     */
    RESOURCE_PACK_LOAD_FAILURE("资源包 / 数据包加载失败"),
    /** 未命中任何已登记签名。 */
    UNKNOWN("未分类");

    private final String label;

    CrashCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
