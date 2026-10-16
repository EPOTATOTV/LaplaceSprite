package io.laplace.sprite.adapters.descriptor;

import io.laplace.sprite.core.model.ArtifactDependency;

import java.util.List;
import java.util.Map;

/**
 * 归一化描述符（14.2 / 14.3 描述符字段的统一视图）。
 *
 * <p>各平台描述符字段名不同、语义近似，这里取并集：插件 id / 主类 / API 版本 / 装载阶段 /
 * 依赖声明 / Folia 支持 / 环境与侧，其余原样放进 {@link #rawFields} 供报告引用。</p>
 *
 * @param id           平台内唯一 id
 * @param name         展示名
 * @param version      声明版本（可能是 semver 区间，原样保留）
 * @param mainClass    入口类
 * @param apiVersions  API 版本列表（Nukkit 的 {@code api:} 与 Bukkit 的 {@code api-version} 都进这里）
 * @param loadPhase    装载阶段（如 Bukkit {@code load: POSTWORLD}）
 * @param dependencies 依赖声明（硬/软/反向）
 * @param foliaSupported 是否声明支持 Folia
 * @param environment  运行环境（Fabric {@code environment}）
 * @param side         侧（server / client / both / proxy）
 * @param rawFields    未被归一化的原始字段
 */
public record PluginDescriptor(String id, String name, String version, String mainClass,
                               List<String> apiVersions, String loadPhase,
                               List<ArtifactDependency> dependencies, boolean foliaSupported,
                               String environment, String side, Map<String, String> rawFields) {

    public PluginDescriptor {
        apiVersions = apiVersions == null ? List.of() : List.copyOf(apiVersions);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        rawFields = rawFields == null ? Map.of() : Map.copyOf(rawFields);
    }
}
