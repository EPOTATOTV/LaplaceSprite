package io.laplace.sprite.adapters.descriptor;

import io.laplace.sprite.core.model.PlatformFamily;

import java.util.List;

/**
 * 描述符解析器注册表（14.2 / 14.3）：按家族取解析器。
 *
 * <p>没有解析器的家族（如 {@code MODPACK} / {@code VANILLA_DATAPACK}）直接报错，
 * 不静默返回空描述符——装配阶段静默失败最难查。</p>
 */
public final class DescriptorParserRegistry {

    private final List<DescriptorParser> parsers;

    public DescriptorParserRegistry() {
        this(defaultParsers());
    }

    public DescriptorParserRegistry(List<DescriptorParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    public static List<DescriptorParser> defaultParsers() {
        return List.of(
                new PaperPluginYmlParser(),
                new PluginYmlParser(),
                new BungeeYmlParser(),
                new VelocityDescriptorParser(),
                new AllayPluginJsonParser(),
                new FabricModJsonParser(),
                new QuiltModJsonParser(),
                new ForgeModsTomlParser(),
                new NeoForgeModsTomlParser(),
                new SpongePluginsJsonParser());
    }

    public boolean supports(PlatformFamily family) {
        return parsers.stream().anyMatch(parser -> parser.supports(family));
    }

    public DescriptorParser parserFor(PlatformFamily family) {
        return parsers.stream()
                .filter(parser -> parser.supports(family))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "无描述符解析器: %s（该家族不是插件/模组平台，或尚未接入）".formatted(family)));
    }

    public PluginDescriptor parse(PlatformFamily family, String content) {
        return parserFor(family).parse(content);
    }

    public List<String> formats() {
        return parsers.stream().map(DescriptorParser::format).toList();
    }
}
