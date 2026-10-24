package io.laplace.sprite.adapters.gametest;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.VersionOrder;

/**
 * 官方 GameTest 在四个模组加载器上的入口（升级实施文档 L-02）。
 *
 * <p>只是声明表，不生成构建脚本：Fabric / Quilt 走 Loom 的客户端 GameTest 任务，
 * Forge / NeoForge 走 GameTest Server 任务；四者运行期的用例输出都汇到
 * {@code /test runall} 的日志形态，由 {@link GameTestSuites} 统一解析。</p>
 */
public enum GameTestLoader {

    FABRIC(PlatformFamily.FABRIC, "fabric-gametest", "runGameTest"),
    QUILT(PlatformFamily.QUILT, "quilt-gametest", "runGameTest"),
    FORGE(PlatformFamily.FORGE, "forge-gametest", "runGameTestServer"),
    NEOFORGE(PlatformFamily.NEOFORGE, "neoforge-gametest", "runGameTestServer");

    /** 四个加载器共用同一条原生命令。 */
    public static final String NATIVE_COMMAND = "/test runall";

    /** GameTest 原生命令进入稳定版的起始版本；26.3 沿用同一条命令。 */
    public static final String MIN_MINECRAFT = "1.21.5";

    private final PlatformFamily family;
    private final String suite;
    private final String gradleTask;

    GameTestLoader(PlatformFamily family, String suite, String gradleTask) {
        this.family = family;
        this.suite = suite;
        this.gradleTask = gradleTask;
    }

    public PlatformFamily family() {
        return family;
    }

    /** 事件流里的套件名。 */
    public String suite() {
        return suite;
    }

    /** 跑该加载器 GameTest 的 Gradle 任务。 */
    public String gradleTask() {
        return gradleTask;
    }

    /** 该加载器的原生命令；四个加载器一致。 */
    public String nativeCommand() {
        return NATIVE_COMMAND;
    }

    /** 按平台家族找加载器；非模组加载器返回空。 */
    public static GameTestLoader of(PlatformFamily family) {
        for (GameTestLoader loader : values()) {
            if (loader.family == family) {
                return loader;
            }
        }
        return null;
    }

    /** 覆盖验收要求的四个加载器。 */
    public static boolean coversAllLoaders() {
        return values().length == 4;
    }

    /**
     * 该版本是否走同一条 GameTest 入口。
     *
     * <p>1.21.5 起 {@code /test} 命令稳定，26.1–26.4 没有改动入口格式；1.22+ / 2.x 尚未发布，
     * 不因为版本号更大就默认支持。</p>
     */
    public static boolean supportsMinecraft(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank()) {
            return false;
        }
        boolean releasedLine = mcVersion.startsWith("26.") || mcVersion.startsWith("1.21.");
        return releasedLine && VersionOrder.compare(mcVersion, MIN_MINECRAFT) >= 0;
    }
}
