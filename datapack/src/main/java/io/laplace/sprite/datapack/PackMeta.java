package io.laplace.sprite.datapack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * pack.mcmeta 的解析结果（15.8 数据包 / 资源包轨）。
 *
 * @param packFormat       声明的 pack_format
 * @param description      描述；text 组件会被拍平成可读文本
 * @param supportedFormats 声明的兼容区间；未声明时为空列表
 * @param namespaceHints   命名空间提示：取 filter 条目里的 namespace，pack.mcmeta 没有别处声明命名空间
 * @param rawFields        原始顶层字段，保留未知字段便于排查
 */
public record PackMeta(int packFormat, String description, List<PackFormatRange> supportedFormats,
                       List<String> namespaceHints, Map<String, Object> rawFields) {

    public PackMeta {
        if (packFormat <= 0) {
            throw new IllegalArgumentException("pack_format 必须为正数: " + packFormat);
        }
        description = description == null ? "" : description;
        supportedFormats = supportedFormats == null ? List.of() : List.copyOf(supportedFormats);
        namespaceHints = namespaceHints == null ? List.of() : List.copyOf(namespaceHints);
        // JSON 里的 null 值会让 Map.copyOf 抛 NPE，这里保留原样做只读包装
        rawFields = rawFields == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(rawFields));
    }

    /** pack_format 本身命中，或落在 supported_formats 的某个区间内。 */
    public boolean supports(int format) {
        if (packFormat == format) {
            return true;
        }
        return supportedFormats.stream().anyMatch(range -> range.contains(format));
    }
}
