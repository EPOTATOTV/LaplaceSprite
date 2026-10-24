package io.laplace.sprite.core.plugin;

import java.util.List;
import java.util.Objects;

/**
 * 插件自述：标识、名称、版本、依赖与能力。
 *
 * @param id           唯一标识，也是依赖引用的键
 * @param name         展示名
 * @param version      版本号
 * @param requires     启动前必须已激活的插件 id
 * @param capabilities 能力标签，供按能力查找
 */
public record PluginDescriptor(String id, String name, String version,
                               List<String> requires, List<String> capabilities) {

    public PluginDescriptor {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("插件 id 不能为空");
        }
        name = name == null || name.isBlank() ? id : name;
        version = version == null ? "" : version;
        requires = requires == null ? List.of() : List.copyOf(requires);
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
}
