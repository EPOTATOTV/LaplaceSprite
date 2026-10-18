package io.laplace.sprite.core.model;

/**
 * 依赖三语义（14.2）：硬依赖缺失即失败；软依赖仅排序与降级；反向声明排序。
 */
public enum DependencySemantics {
    HARD,
    SOFT,
    REVERSE
}
