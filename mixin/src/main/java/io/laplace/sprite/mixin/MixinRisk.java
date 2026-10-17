package io.laplace.sprite.mixin;

/**
 * Mixin 冲突风险等级（15.11 P1）。
 *
 * <p>顺序即严重度：{@code @Overwrite} 整体替换方法字节码，风险最高；同一调用/字段被重复
 * {@code @Redirect} 次之（官方明确一次调用/字段只能重定向一次）；普通 {@code @Inject}
 * 重叠最轻，只说明多个 mod 注入了同一个类。</p>
 */
public enum MixinRisk {

    OVERWRITE(3),
    DUPLICATE_REDIRECT(2),
    INJECT_OVERLAP(1);

    private final int severity;

    MixinRisk(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }
}
