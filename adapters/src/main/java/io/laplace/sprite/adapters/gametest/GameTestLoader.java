package io.laplace.sprite.adapters.gametest;

import io.laplace.sprite.core.model.PlatformFamily;

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
}
