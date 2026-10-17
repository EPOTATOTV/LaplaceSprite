package io.laplace.sprite.mixin;

import java.util.Objects;

/**
 * 一条注入点数量声明（15.11）：mixin 配置里声明的 {@code require}/{@code expect} 计数。
 *
 * @param modId         模组 id
 * @param targetClass   被织入的目标类（全限定名）
 * @param kind          注入方式
 * @param declaredCount 声明的注入点数量
 * @param requireMode   require（精确）或 expect（软约束）
 * @param method        注入目标方法；字段重定向时填字段名，未知为空
 * @param note          备注，可为空串
 */
public record InjectionDeclaration(String modId, String targetClass, MixinInjectionKind kind,
                                   int declaredCount, MixinRequireMode requireMode, String method,
                                   String note) {

    public InjectionDeclaration {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(targetClass, "targetClass");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(requireMode, "requireMode");
        if (declaredCount < 0) {
            throw new IllegalArgumentException("声明的注入点数量不能为负: " + declaredCount);
        }
        method = method == null || method.isBlank() ? null : method;
        note = note == null ? "" : note;
    }

    /** 冲突图谱里判断「同一次调用/字段被重复重定向」的身份键。 */
    public String siteKey() {
        return modId + "#" + targetClass + "#" + kind.name() + "#" + (method == null ? "*" : method);
    }
}
