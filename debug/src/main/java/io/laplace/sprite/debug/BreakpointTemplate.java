package io.laplace.sprite.debug;

/**
 * 条件断点模板（15.1）。
 *
 * @param id         模板 id
 * @param name       名称
 * @param scene      适用场景
 * @param expression 断点条件表达式（在调试 JVM 里求值）
 * @param notes      映射 / 版本差异说明
 */
public record BreakpointTemplate(String id, String name, String scene, String expression, String notes) {

    public BreakpointTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("模板 id 不能为空");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (scene == null || scene.isBlank()) {
            throw new IllegalArgumentException("适用场景不能为空");
        }
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("断点表达式不能为空");
        }
        notes = notes == null ? "" : notes;
    }
}
