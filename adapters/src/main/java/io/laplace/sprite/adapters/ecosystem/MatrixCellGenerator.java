package io.laplace.sprite.adapters.ecosystem;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.laplace.sprite.adapters.matrix.MatrixPlan;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.VersionOrder;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 矩阵格自动生成器（14.9）：版本清单 → 笛卡尔积 → 兼容裁剪 → matrix JSON。
 *
 * <p>输入全部走 {@link VersionSource}，默认是离线快照；生成过程不访问网络。
 * 裁剪规则只有一条——基线模组在候选 (MC 版本, 加载器) 上没有可用构建就丢掉整格，
 * 并把原因写进结果，失败回溯时能看到「为什么这一格没跑」。</p>
 */
public final class MatrixCellGenerator {

    /** 生成器覆盖的加载器线；Quilt 复用 Fabric，不单独成格。 */
    public static final List<String> LOADERS = List.of("fabric", "neoforge", "forge");
    public static final String PAPER = "paper";

    /** 26.1 起 Minecraft 强制 Java 25，低于它仍用 21。 */
    private static final String JAVA_25_FROM = "26.1";
    private static final int JAVA_25 = 25;
    private static final int JAVA_DEFAULT = 21;

    /**
     * 一格定义，字段与 {@link MatrixPlan.MatrixJob} 对齐，另加 cell / bundles 便于回溯。
     *
     * @param cellId       格标识（platform/mc/loader）
     * @param family       家族
     * @param platform     平台名
     * @param mc           Minecraft 版本
     * @param loader       加载器版本；插件线为 null
     * @param adapterId    适配器 id
     * @param jdk          该 job 的 JDK
     * @param jobLine      独立 job 分组
     * @param bundles      注入的生态包
     * @param baselineMods 基线模组列表
     */
    public record CellDefinition(String cellId, PlatformFamily family, String platform, String mc, String loader,
                                 String adapterId, int jdk, String jobLine, List<String> bundles,
                                 List<String> baselineMods) {

        public CellDefinition {
            Objects.requireNonNull(cellId, "cellId");
            Objects.requireNonNull(family, "family");
            bundles = bundles == null ? List.of() : List.copyOf(bundles);
            baselineMods = baselineMods == null ? List.of() : List.copyOf(baselineMods);
        }
    }

    /**
     * 被裁掉的格。
     *
     * @param cell   格定义
     * @param reason 裁剪原因
     */
    public record DroppedCell(CellDefinition cell, String reason) {
    }

    /**
     * 生成结果。
     *
     * @param tier       门禁档位
     * @param maxParallel 并发度
     * @param failFast   是否快速失败
     * @param cells      保留的格
     * @param dropped    被裁掉的格
     */
    public record GenerationResult(String tier, int maxParallel, boolean failFast,
                                   List<CellDefinition> cells, List<DroppedCell> dropped) {

        public GenerationResult {
            cells = List.copyOf(cells);
            dropped = List.copyOf(dropped);
        }

        /** MatrixPlan 兼容的 JSON：include 数组 + 裁剪记录。 */
        public String toJson() {
            ObjectNode root = Json.jsonMapper().createObjectNode();
            root.put("tier", tier);
            root.put("fail-fast", failFast);
            root.put("max-parallel", maxParallel);
            ArrayNode include = root.putArray("include");
            cells.forEach(cell -> include.add(cellJson(cell)));
            ArrayNode droppedArray = root.putArray("dropped");
            for (DroppedCell drop : dropped) {
                ObjectNode node = droppedArray.addObject();
                node.set("cell", cellJson(drop.cell()));
                node.put("reason", drop.reason());
            }
            try {
                return Json.jsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(root);
            } catch (IOException ex) {
                throw new UncheckedIOException("序列化矩阵 JSON 失败", ex);
            }
        }

        /** GitHub Actions 的 {@code strategy.matrix.include} 片段。 */
        public String toIncludeFragment() {
            StringBuilder out = new StringBuilder("strategy:\n");
            out.append("  fail-fast: ").append(failFast).append('\n');
            out.append("  max-parallel: ").append(maxParallel).append('\n');
            if (cells.isEmpty()) {
                out.append("  matrix:\n    include: []\n");
                return out.toString();
            }
            out.append("  matrix:\n    include:\n");
            for (CellDefinition cell : cells) {
                out.append("      - { cell: ").append(cell.cellId())
                        .append(", platform: ").append(cell.platform())
                        .append(", mc: \"").append(cell.mc()).append('"');
                if (cell.loader() != null) {
                    out.append(", loader: \"").append(cell.loader()).append('"');
                }
                out.append(", adapter: ").append(cell.adapterId())
                        .append(", jdk: ").append(cell.jdk())
                        .append(", job-line: ").append(cell.jobLine())
                        .append(", bundles: [").append(String.join(", ", cell.bundles())).append("]")
                        .append(" }\n");
            }
            return out.toString();
        }

        /** 格定义落盘，失败时按 cell / mc / loader 回溯到具体版本。 */
        public void writeCells(Path file) {
            Objects.requireNonNull(file, "file");
            try {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, toJson(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                throw new UncheckedIOException("写入格定义失败: " + file, ex);
            }
        }
    }

    private final VersionSource source;

    public MatrixCellGenerator(VersionSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** 默认离线生成器。 */
    public static MatrixCellGenerator offline() {
        return new MatrixCellGenerator(OfflineVersionSnapshot.loadDefault());
    }

    public GenerationResult generate(GateTier tier) {
        Objects.requireNonNull(tier, "tier");
        List<CellDefinition> cells = new ArrayList<>();
        List<DroppedCell> dropped = new ArrayList<>();

        for (String mc : selectMinecraftVersions(tier)) {
            if (tier.includesPluginLine()) {
                addPaperCell(tier, mc, cells, dropped);
            }
            for (String loader : LOADERS) {
                addLoaderCell(tier, mc, loader, cells, dropped);
            }
        }
        return new GenerationResult(tier.cliName(), tier.maxParallel(), tier.failFast(), cells, dropped);
    }

    private void addPaperCell(GateTier tier, String mc, List<CellDefinition> cells,
                              List<DroppedCell> dropped) {
        CellDefinition cell = new CellDefinition(cellId(PAPER, mc, null), PlatformFamily.PAPER, PAPER, mc, null,
                PAPER, jdkFor(mc), MatrixPlan.JOB_LINE_DEFAULT, tier.bundlesFor(true), List.of());
        if (source.paperBuild(mc).isPresent()) {
            cells.add(cell);
        } else {
            dropped.add(new DroppedCell(cell, "PaperMC STABLE 通道无 " + mc + " 构建"));
        }
    }

    private void addLoaderCell(GateTier tier, String mc, String loader, List<CellDefinition> cells,
                               List<DroppedCell> dropped) {
        PlatformFamily family = familyOf(loader);
        CellDefinition base = new CellDefinition(cellId(loader, mc, null), family, loader, mc, null,
                loader, jdkFor(mc), MatrixPlan.JOB_LINE_DEFAULT, tier.bundlesFor(false), List.of());
        Optional<VersionSource.LoaderRelease> release = latestStable(mc, loader);
        if (release.isEmpty()) {
            dropped.add(new DroppedCell(base, loader + " 在 " + mc + " 无稳定加载器版本"));
            return;
        }
        BaselineBundle baseline = BaselineBundle.forLoader(family);
        Optional<String> missing = firstMissingBuild(baseline, mc, loader);
        if (missing.isPresent()) {
            dropped.add(new DroppedCell(base,
                    "基线模组 " + missing.get() + " 在 (" + mc + ", " + loader + ") 无可用构建"));
            return;
        }
        String loaderVersion = release.get().loaderVersion();
        cells.add(new CellDefinition(cellId(loader, mc, loaderVersion), family, loader, mc, loaderVersion,
                loader, jdkFor(mc), MatrixPlan.JOB_LINE_DEFAULT, tier.bundlesFor(false),
                baseline.projectIds()));
    }

    /** 兼容裁剪：基线里任一项目没有 (MC, 加载器) 构建，整格不跑。 */
    private Optional<String> firstMissingBuild(BaselineBundle baseline, String mc, String loader) {
        return baseline.projectIds().stream()
                .filter(project -> !source.hasBuild(project, mc, loader))
                .findFirst();
    }

    private Optional<VersionSource.LoaderRelease> latestStable(String mc, String loader) {
        return source.loaderReleases(loader).stream()
                .filter(release -> release.minecraft().equals(mc) && release.stable())
                .max(Comparator.comparing(VersionSource.LoaderRelease::loaderVersion, VersionOrder::compare));
    }

    private List<String> selectMinecraftVersions(GateTier tier) {
        List<VersionSource.MinecraftRelease> releases = source.minecraftReleases().stream()
                .filter(release -> "stable".equalsIgnoreCase(release.channel()))
                .sorted(Comparator.comparing(VersionSource.MinecraftRelease::version, VersionOrder::compare).reversed())
                .toList();
        LinkedHashSet<String> versions = new LinkedHashSet<>();
        if (tier.latestLtsOnly()) {
            releases.stream()
                    .filter(VersionSource.MinecraftRelease::lts)
                    .findFirst()
                    .or(() -> releases.stream().findFirst())
                    .ifPresent(release -> versions.add(release.version()));
        } else {
            for (VersionSource.MinecraftRelease release : releases) {
                if (versions.size() >= tier.minecraftVersionLimit()) {
                    break;
                }
                versions.add(release.version());
            }
        }
        return List.copyOf(versions);
    }

    private int jdkFor(String mc) {
        if (VersionOrder.compare(mc, JAVA_25_FROM) >= 0) {
            return JAVA_25;
        }
        return source.minecraftReleases().stream()
                .filter(release -> release.version().equals(mc))
                .findFirst()
                .map(VersionSource.MinecraftRelease::java)
                .orElse(JAVA_DEFAULT);
    }

    private static PlatformFamily familyOf(String loader) {
        return switch (loader) {
            case "fabric" -> PlatformFamily.FABRIC;
            case "neoforge" -> PlatformFamily.NEOFORGE;
            case "forge" -> PlatformFamily.FORGE;
            default -> throw new IllegalArgumentException("未知加载器: " + loader);
        };
    }

    private static String cellId(String platform, String mc, String loader) {
        String base = platform.toLowerCase(Locale.ROOT) + "/" + mc;
        return loader == null ? base : base + "/" + loader;
    }

    private static ObjectNode cellJson(CellDefinition cell) {
        ObjectNode node = Json.jsonMapper().createObjectNode();
        node.put("cell", cell.cellId());
        node.put("platform", cell.platform());
        node.put("mc", cell.mc());
        if (cell.loader() == null) {
            node.putNull("loader");
        } else {
            node.put("loader", cell.loader());
        }
        node.put("adapter", cell.adapterId());
        node.put("jdk", cell.jdk());
        node.put("job-line", cell.jobLine());
        ArrayNode bundles = node.putArray("bundles");
        cell.bundles().forEach(bundles::add);
        ArrayNode baseline = node.putArray("baseline");
        cell.baselineMods().forEach(baseline::add);
        return node;
    }
}
