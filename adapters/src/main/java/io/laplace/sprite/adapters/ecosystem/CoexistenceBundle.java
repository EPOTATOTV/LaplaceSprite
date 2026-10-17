package io.laplace.sprite.adapters.ecosystem;

import io.laplace.sprite.core.model.PlatformFamily;

import java.util.ArrayList;
import java.util.List;

/**
 * 内容型模组共存回归：Create × Alex's Mobs Continued（14.9）。
 *
 * <p>Fabric 格用 Create Fabric + Continued 的 Fabric 构建；Forge / NeoForge 格用 Create 本体
 * + Continued。两个模组都改实体与方块注册，共存问题通常先以 duplicate registry entry
 * 或 missing dependency 的形式出现在启动期。</p>
 */
public final class CoexistenceBundle {

    public static final String CREATE = "create";
    public static final String ALEXS_MOBS_CONTINUED = "alexs-mobs-continued";

    /** 单模组对照容器：同版本并行跑，用来区分是谁引入的栈帧。 */
    public record ControlCell(String cellId, List<String> coordinates) {
    }

    /**
     * 一条共存模组。
     *
     * @param id         模组标识
     * @param projectId  Modrinth 项目 slug
     * @param coordinate 产物坐标
     * @param loader     构建所属加载器
     * @param note       该加载器下取哪个构建
     */
    public record ModArtifact(String id, String projectId, String coordinate, String loader, String note) {
    }

    private static final List<BundleAssertion> ASSERTIONS = List.of(
            new BundleAssertion("startup-clean",
                    "启动期无 duplicate registry entry / missing dependency"),
            new BundleAssertion("summon-alex-mob",
                    "/summon Alex 生物成功，控制台无实体异常"),
            new BundleAssertion("place-create-machinery",
                    "放置 Create 机械成功，无实体崩溃"),
            new BundleAssertion("client-chunk-loading",
                    "客户端加入后区块加载 5 分钟无 tick 异常"));

    private final PlatformFamily family;
    private final List<ModArtifact> artifacts;

    private CoexistenceBundle(PlatformFamily family, List<ModArtifact> artifacts) {
        this.family = family;
        this.artifacts = List.copyOf(artifacts);
    }

    public static CoexistenceBundle forLoader(PlatformFamily family) {
        PlatformFamily loader = LoaderSupport.requireModLoader(family);
        String loaderId = LoaderSupport.loaderId(loader);
        String createProject = loader == PlatformFamily.FABRIC ? "create-fabric" : CREATE;
        String createNote = loader == PlatformFamily.FABRIC
                ? "Fabric 侧取 Create Fabric 构建"
                : "Forge / NeoForge 侧取 Create 本体构建";
        List<ModArtifact> artifacts = new ArrayList<>();
        artifacts.add(new ModArtifact(CREATE, createProject, coordinate(createProject), loaderId, createNote));
        artifacts.add(new ModArtifact(ALEXS_MOBS_CONTINUED, ALEXS_MOBS_CONTINUED,
                coordinate(ALEXS_MOBS_CONTINUED), loaderId, "两个加载器共用同一 Continued 项目，按加载器取对应构建"));
        return new CoexistenceBundle(loader, artifacts);
    }

    public PlatformFamily family() {
        return family;
    }

    public List<ModArtifact> artifacts() {
        return artifacts;
    }

    public List<String> coordinates() {
        return artifacts.stream().map(ModArtifact::coordinate).toList();
    }

    public List<BundleAssertion> assertions() {
        return ASSERTIONS;
    }

    /** 只装单模组的对照格：同版本并行，只有被测目标引入后才出现的栈帧才记为其缺陷。 */
    public List<ControlCell> controlCells() {
        List<ControlCell> cells = new ArrayList<>();
        for (ModArtifact artifact : artifacts) {
            cells.add(new ControlCell(family.name().toLowerCase() + "-" + artifact.id(), List.of(artifact.coordinate())));
        }
        return List.copyOf(cells);
    }

    /** 归因口径，写进报告。 */
    public static String attributionRule() {
        return "只装单模组的对照格同版本并行；被测目标引入后才出现的栈帧才记为其缺陷";
    }

    private static String coordinate(String projectId) {
        return "modrinth:" + projectId;
    }
}
