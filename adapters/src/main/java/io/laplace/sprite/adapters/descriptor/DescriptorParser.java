package io.laplace.sprite.adapters.descriptor;

import io.laplace.sprite.core.model.PlatformFamily;

/**
 * 平台描述符解析器（14.2 / 14.3）。
 */
public interface DescriptorParser {

    /** 本解析器负责哪些家族。 */
    boolean supports(PlatformFamily family);

    PluginDescriptor parse(String content);

    /** 报告里用的格式名。 */
    default String format() {
        return getClass().getSimpleName();
    }
}
