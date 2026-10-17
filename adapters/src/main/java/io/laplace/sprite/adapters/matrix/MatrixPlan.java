package io.laplace.sprite.adapters.matrix;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterRegistryFactory;
import io.laplace.sprite.adapters.matrix.AffectedFamilySelector.Selection;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;
import io.laplace.sprite.core.spi.VersionOrder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CI 矩阵计划（14.7）：把受影响族选择摊成 GitHub Actions 的 {@code strategy.matrix.include}。
 *
 * <p>PR 门禁只跑第一梯队（∩ 受影响族），模组线取当前 Minecraft 档的最新加载器版本；
 * 夜间基准跑全矩阵（排除 EXCLUDED 家族，模组线跑矩阵里所有档位）。基岩线与代理线各自成组
 * （{@code job-line}）并用不同 JDK，供 workflow 拆成独立 job。</p>
 */
public final class MatrixPlan {

    public static final String JOB_LINE_DEFAULT = "default";
    public static final String JOB_LINE_PROXY = "proxy";
    public static final String JOB_LINE_BEDROCK = "bedrock";

    /** 缺省 Minecraft 档，与文档 CI 片段一致。 */
    public static final String DEFAULT_MINECRAFT = "1.21.11";

    /** 基岩系官方要求 JDK 21+（14.3）。 */
    private static final int JDK_BEDROCK = 21;
    /** 代理线按 Velocity 3.x 的运行下限。 */
    private static final int JDK_PROXY = 17;
    private static final int JDK_DEFAULT = 21;

    private static final List<PlatformFamily> ALL_FAMILIES = List.of(PlatformFamily.values());

    private static final Map<PlatformFamily, String> ADAPTER_IDS = AdapterRegistryFactory.allAdapters().stream()
            .collect(Collectors.toUnmodifiableMap(AbstractLaplaceAdapter::family, AbstractLaplaceAdapter::id,
                    (first, second) -> first));

    /**
     * 一条 CI 矩阵项。
     *
     * @param family   家族
     * @param platform 平台名（家族小写）
     * @param mc       Minecraft 版本
     * @param loader   加载器版本；非模组线为空
     * @param adapterId 已注册的适配器 id
     * @param jdk      该 job 的 JDK 版本
     * @param jobLine  独立 job 分组（default / proxy / bedrock）
     */
    public record MatrixJob(PlatformFamily family, String platform, String mc, String loader,
                            String adapterId, int jdk, String jobLine) {

        public MatrixJob {
            Objects.requireNonNull(family, "family");
            Objects.requireNonNull(platform, "platform");
            Objects.requireNonNull(mc, "mc");
            Objects.requireNonNull(jobLine, "jobLine");
        }
    }

    private final List<MatrixJob> jobs;
    private final boolean fullMatrix;
    private final String reason;

    private MatrixPlan(List<MatrixJob> jobs, boolean fullMatrix, String reason) {
        this.jobs = List.copyOf(jobs);
        this.fullMatrix = fullMatrix;
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** PR 门禁：第一梯队 ∩ 受影响族。 */
    public static MatrixPlan forPullRequest(Selection selection, VersionMatrixRegistry matrix) {
        return forPullRequest(selection, matrix, DEFAULT_MINECRAFT);
    }

    public static MatrixPlan forPullRequest(Selection selection, VersionMatrixRegistry matrix,
                                            String minecraftVersion) {
        List<MatrixJob> jobs = new ArrayList<>();
        Set<PlatformFamily> families = gateFamilies(selection);
        for (PlatformFamily family : families) {
            jobs.addAll(jobsFor(family, matrix, minecraftVersion, false));
        }
        return new MatrixPlan(jobs, false, selection.empty()
                ? selection.reason()
                : "PR 门禁：第一梯队 ∩ 受影响族（%d 个家族）".formatted(families.size()));
    }

    /** 夜间基准：全矩阵。 */
    public static MatrixPlan forNightly(VersionMatrixRegistry matrix) {
        return forNightly(matrix, DEFAULT_MINECRAFT);
    }

    public static MatrixPlan forNightly(VersionMatrixRegistry matrix, String minecraftVersion) {
        Set<PlatformFamily> families = gatedFamilies();
        List<MatrixJob> jobs = new ArrayList<>();
        for (PlatformFamily family : families) {
            jobs.addAll(jobsFor(family, matrix, minecraftVersion, true));
        }
        return new MatrixPlan(jobs, true, "夜间基准：全矩阵（%d 个家族，排除 EOL）".formatted(families.size()));
    }

    public List<MatrixJob> jobs() {
        return jobs;
    }

    public boolean fullMatrix() {
        return fullMatrix;
    }

    public String reason() {
        return reason;
    }

    /** 渲染成 {@code strategy.matrix.include} 片段，workflow 直接消费。 */
    public String toMatrixYaml() {
        if (jobs.isEmpty()) {
            return "strategy:\n  fail-fast: false\n  matrix:\n    include: []\n";
        }
        StringBuilder out = new StringBuilder("strategy:\n  fail-fast: false\n  matrix:\n    include:\n");
        for (MatrixJob job : jobs) {
            out.append("      - { platform: ").append(job.platform())
                    .append(", mc: \"").append(job.mc()).append('"');
            if (job.loader() != null) {
                out.append(", loader: \"").append(job.loader()).append('"');
            }
            if (job.adapterId() != null) {
                out.append(", adapter: ").append(job.adapterId());
            }
            out.append(", jdk: ").append(job.jdk())
                    .append(", job-line: ").append(job.jobLine())
                    .append(" }\n");
        }
        return out.toString();
    }

    /** PR 门禁家族：只取第一梯队；受影响族收窄不出结果时退回整个第一梯队。 */
    private static Set<PlatformFamily> gateFamilies(Selection selection) {
        Set<PlatformFamily> tier1 = new LinkedHashSet<>();
        for (PlatformFamily family : ALL_FAMILIES) {
            if (FamilyTier.of(family).gatesPullRequest()) {
                tier1.add(family);
            }
        }
        if (selection.empty()) {
            // 文档 / CI 改动不选任何家族，是否空跑交给调用方。
            return Set.of();
        }
        if (selection.fullMatrixRequired()) {
            // 共享代码 / 无法判定：PR 门禁跑整个第一梯队，全矩阵留给夜间。
            return tier1;
        }
        Set<PlatformFamily> affected = new LinkedHashSet<>(selection.families());
        affected.retainAll(tier1);
        // 受影响族不在第一梯队时（如只改了 Cardboard），收窄不出门禁，退回整个第一梯队。
        return affected.isEmpty() ? tier1 : affected;
    }

    private static Set<PlatformFamily> gatedFamilies() {
        Set<PlatformFamily> families = new LinkedHashSet<>();
        for (PlatformFamily family : ALL_FAMILIES) {
            if (FamilyTier.of(family) != FamilyTier.EXCLUDED) {
                families.add(family);
            }
        }
        return families;
    }

    private static List<MatrixJob> jobsFor(PlatformFamily family, VersionMatrixRegistry matrix,
                                           String minecraftVersion, boolean nightly) {
        List<VersionMatrixEntry> entries = matrix.entries(family);
        List<VersionMatrixEntry> selected = nightly ? entries : currentEntries(entries, minecraftVersion);
        if (selected.isEmpty()) {
            // 家族没有版本矩阵来源（Bukkit / 代理 / 基岩 / Sponge 等）：出一条不含加载器版本的占位项，
            // 运行期由矩阵校验报错，而不是在这里猜版本。
            return List.of(job(family, minecraftVersion, null));
        }
        return selected.stream()
                .map(entry -> job(family, entry.minecraftVersion(), entry.loaderVersion()))
                .toList();
    }

    /** 当前档只取一条：同档位有多条加载器版本时取最高的一条。 */
    private static List<VersionMatrixEntry> currentEntries(List<VersionMatrixEntry> entries,
                                                           String minecraftVersion) {
        List<VersionMatrixEntry> sameMinecraft = entries.stream()
                .filter(entry -> minecraftVersion.equals(entry.minecraftVersion()))
                .toList();
        // 当前档不在矩阵里就退回矩阵里版本最高的一条，避免产出没有加载器版本的模组线项。
        List<VersionMatrixEntry> pool = sameMinecraft.isEmpty() ? entries : sameMinecraft;
        return pool.stream()
                .max(Comparator.comparing(VersionMatrixEntry::minecraftVersion, VersionOrder::compare)
                        .thenComparing(VersionMatrixEntry::loaderVersion,
                                Comparator.nullsFirst(VersionOrder::compare)))
                .map(List::of)
                .orElseGet(List::of);
    }

    private static MatrixJob job(PlatformFamily family, String minecraftVersion, String loaderVersion) {
        return new MatrixJob(family, family.name().toLowerCase(Locale.ROOT), minecraftVersion, loaderVersion,
                ADAPTER_IDS.get(family), jdkFor(family), jobLineFor(family));
    }

    private static int jdkFor(PlatformFamily family) {
        if (family.isBedrock()) {
            return JDK_BEDROCK;
        }
        return family.isProxy() ? JDK_PROXY : JDK_DEFAULT;
    }

    private static String jobLineFor(PlatformFamily family) {
        if (family.isBedrock()) {
            return JOB_LINE_BEDROCK;
        }
        return family.isProxy() ? JOB_LINE_PROXY : JOB_LINE_DEFAULT;
    }
}
