package io.laplace.sprite.render;

import java.util.List;
import java.util.Objects;

/**
 * 光影包声明（15.12 P2）。
 *
 * <p>内置低负载档只作为数据声明，jar/zip 不随本模块提交；真实运行时由测试资源提供。</p>
 *
 * @param name   光影包名
 * @param path   放置路径
 * @param weight 负载档
 */
public record ShaderPackSpec(String name, String path, ShaderPackWeight weight) {

    /** 内置轻量档：Sodium+Iris 冒烟格使用。 */
    public static final ShaderPackSpec LOW_LOAD = new ShaderPackSpec(
            "laplace-lowload", "shaderpacks/laplace-lowload.zip", ShaderPackWeight.LOW);

    public ShaderPackSpec {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(weight, "weight");
    }

    public static List<ShaderPackSpec> bundled() {
        return List.of(LOW_LOAD);
    }

    public boolean lightweight() {
        return weight == ShaderPackWeight.LOW;
    }
}
