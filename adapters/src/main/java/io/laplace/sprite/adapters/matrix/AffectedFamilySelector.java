package io.laplace.sprite.adapters.matrix;

import io.laplace.sprite.adapters.descriptor.AllayPluginJsonParser;
import io.laplace.sprite.adapters.descriptor.BungeeYmlParser;
import io.laplace.sprite.adapters.descriptor.DescriptorParser;
import io.laplace.sprite.adapters.descriptor.FabricModJsonParser;
import io.laplace.sprite.adapters.descriptor.ForgeModsTomlParser;
import io.laplace.sprite.adapters.descriptor.NeoForgeModsTomlParser;
import io.laplace.sprite.adapters.descriptor.PaperPluginYmlParser;
import io.laplace.sprite.adapters.descriptor.PluginYmlParser;
import io.laplace.sprite.adapters.descriptor.QuiltModJsonParser;
import io.laplace.sprite.adapters.descriptor.SpongePluginsJsonParser;
import io.laplace.sprite.adapters.descriptor.VelocityDescriptorParser;
import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 受影响族选择（14.7 组合爆炸控制）：把变更路径换算成需要重跑的家族集合。
 *
 * <p>规则（按判定顺序）：
 * <ol>
 *   <li>文档与 CI 配置（{@code docs/}、{@code .github/}、{@code *.md}、LICENSE）不选任何家族，
 *       是否空跑由调用方决定。</li>
 *   <li>{@code adapters/.../adapters/<子包>/} 按子包映射：{@code bukkit} → Bukkit 家族、
 *       {@code proxy} → 代理线、{@code bedrock} → 基岩线、{@code modloader} → 模组线、
 *       {@code sponge} → Sponge 系、{@code hybrid} → 混合端。</li>
 *   <li>{@code descriptor} 子包按解析器服务范围映射：改哪个解析器，就重跑它
 *       {@code supports()} 覆盖的那些家族（受影响集合由解析器自己推导，不另维护清单）。</li>
 *   <li>共享代码一律按全家族处理并置 {@code fullMatrixRequired}：{@code core} / {@code servers}
 *       等其它模块、{@code adapters} 包根骨架、{@code artifact / event / lifecycle / matrix /
 *       deps / support / sim} 等共享子包、以及 adapters 测试代码与资源。这些改动无法收窄到某个家族。</li>
 *   <li>无法判定的路径（含空输入）同样返回全家族并置 {@code fullMatrixRequired}——
 *       存疑时答案是「全部家族」，这是明确的规则，不是兜底意外。</li>
 * </ol>
 */
public final class AffectedFamilySelector {

    private static final String ADAPTER_PACKAGE_MARKER = "io/laplace/sprite/adapters/";

    private static final List<PlatformFamily> ALL_FAMILIES = List.of(PlatformFamily.values());

    /** 适配器子包 → 受影响家族；同族判定沿用 PlatformFamily 的分类谓词，避免两处清单各写一份。 */
    private static final Map<String, Set<PlatformFamily>> SUBPACKAGE_FAMILIES = Map.of(
            "bukkit", familiesWhere(family -> family.isPluginPlatform()
                    && !family.isProxy() && !family.isBedrock()),
            "proxy", familiesWhere(PlatformFamily::isProxy),
            "bedrock", familiesWhere(PlatformFamily::isBedrock),
            "modloader", familiesWhere(family -> family.isModLoader() && !family.isSponge()),
            "sponge", familiesWhere(PlatformFamily::isSponge),
            "hybrid", familiesWhere(PlatformFamily::isHybrid));

    /** 描述符解析器文件 → 实例；文件名取归一化后的小写形式。 */
    private static final Map<String, DescriptorParser> DESCRIPTOR_PARSERS = Map.ofEntries(
            Map.entry("paperpluginymlparser.java", new PaperPluginYmlParser()),
            Map.entry("pluginymlparser.java", new PluginYmlParser()),
            Map.entry("bungeeymlparser.java", new BungeeYmlParser()),
            Map.entry("velocitydescriptorparser.java", new VelocityDescriptorParser()),
            Map.entry("allaypluginjsonparser.java", new AllayPluginJsonParser()),
            Map.entry("fabricmodjsonparser.java", new FabricModJsonParser()),
            Map.entry("quiltmodjsonparser.java", new QuiltModJsonParser()),
            Map.entry("forgemodstomlparser.java", new ForgeModsTomlParser()),
            Map.entry("neoforgemodstomlparser.java", new NeoForgeModsTomlParser()),
            Map.entry("spongepluginsjsonparser.java", new SpongePluginsJsonParser()));

    /** 选择结果。 */
    public record Selection(Set<PlatformFamily> families, String reason, boolean fullMatrixRequired) {

        public Selection {
            Objects.requireNonNull(reason, "reason");
            families = families == null ? Set.of() : ordered(families);
        }

        public boolean empty() {
            return families.isEmpty();
        }
    }

    public Selection select(List<String> changedPaths) {
        Objects.requireNonNull(changedPaths, "changedPaths");
        List<String> normalized = changedPaths.stream()
                .filter(Objects::nonNull)
                .map(AffectedFamilySelector::normalize)
                .filter(path -> !path.isBlank())
                .toList();
        if (normalized.isEmpty()) {
            // 没有变更路径就无从判定，按最保守口径返回全家族。
            return new Selection(new LinkedHashSet<>(ALL_FAMILIES), "未提供变更路径，按全家族保守处理", true);
        }

        List<String> codePaths = normalized.stream()
                .filter(path -> !isDocumentationOrCi(path))
                .toList();
        if (codePaths.isEmpty()) {
            return new Selection(Set.of(), "仅文档或 CI 配置改动，不选任何家族", false);
        }

        Set<PlatformFamily> families = new LinkedHashSet<>();
        List<String> notes = new ArrayList<>();
        boolean fullMatrixRequired = false;
        for (String path : codePaths) {
            Affected affected = affectedBy(path);
            families.addAll(affected.families());
            fullMatrixRequired |= affected.allFamilies();
            notes.add("%s → %s".formatted(path, affected.note()));
        }
        return new Selection(families, String.join("；", notes), fullMatrixRequired);
    }

    /** 单条路径的受影响因素。 */
    private record Affected(Set<PlatformFamily> families, boolean allFamilies, String note) {

        static Affected all(String note) {
            return new Affected(new LinkedHashSet<>(ALL_FAMILIES), true, note);
        }

        static Affected of(Set<PlatformFamily> families, String note) {
            return new Affected(new LinkedHashSet<>(families), false, note);
        }
    }

    private static Affected affectedBy(String path) {
        int marker = path.indexOf(ADAPTER_PACKAGE_MARKER);
        if (marker < 0) {
            return Affected.all("不在适配器包内（内核 / 其它模块 / 构建脚本 / 资源），按全家族处理");
        }
        String remainder = path.substring(marker + ADAPTER_PACKAGE_MARKER.length());
        int slash = remainder.indexOf('/');
        if (slash < 0) {
            return Affected.all("adapters 包根骨架或适配器测试代码，按全家族处理");
        }
        String subpackage = remainder.substring(0, slash);
        if ("descriptor".equals(subpackage)) {
            return descriptorAffected(remainder.substring(slash + 1));
        }
        Set<PlatformFamily> mapped = SUBPACKAGE_FAMILIES.get(subpackage);
        if (mapped != null) {
            return Affected.of(mapped, "%s/ 改动只影响 %s".formatted(subpackage, joinNames(mapped)));
        }
        return Affected.all("%s/ 是共享实现，按全家族处理".formatted(subpackage));
    }

    private static Affected descriptorAffected(String fileName) {
        DescriptorParser parser = DESCRIPTOR_PARSERS.get(fileName);
        if (parser == null) {
            return Affected.all("描述符公共代码 %s 影响所有带描述符的家族，按全家族处理".formatted(fileName));
        }
        Set<PlatformFamily> served = familiesWhere(parser::supports);
        return Affected.of(served, "%s 服务于 %s".formatted(fileName, joinNames(served)));
    }

    private static boolean isDocumentationOrCi(String path) {
        return path.startsWith("docs/")
                || path.startsWith(".github/")
                || path.endsWith(".md")
                || path.equals("license")
                || path.equals("readme");
    }

    private static String normalize(String path) {
        return path.replace('\\', '/').trim().toLowerCase(Locale.ROOT);
    }

    private static Set<PlatformFamily> familiesWhere(Predicate<PlatformFamily> predicate) {
        Set<PlatformFamily> matched = new LinkedHashSet<>();
        for (PlatformFamily family : ALL_FAMILIES) {
            if (predicate.test(family)) {
                matched.add(family);
            }
        }
        return matched;
    }

    private static Set<PlatformFamily> ordered(Set<PlatformFamily> families) {
        List<PlatformFamily> sorted = new ArrayList<>(families);
        sorted.sort(Comparator.comparingInt(Enum::ordinal));
        return Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    private static String joinNames(Set<PlatformFamily> families) {
        return String.join(", ", families.stream().map(Enum::name).toList());
    }
}
