package io.laplace.sprite.mixin;

/**
 * Mixin 注入方式（15.11）。
 *
 * <p>只列出门禁与冲突图谱关心的四种；日志里识别不出的情况落到 {@link #UNKNOWN}。</p>
 */
public enum MixinInjectionKind {

    INJECT("@Inject"),
    REDIRECT("@Redirect"),
    MODIFY_VARIABLE("@ModifyVariable"),
    OVERWRITE("@Overwrite"),
    UNKNOWN("unknown");

    private final String annotation;

    MixinInjectionKind(String annotation) {
        this.annotation = annotation;
    }

    /** 日志与报告里使用的注解写法。 */
    public String annotation() {
        return annotation;
    }
}
