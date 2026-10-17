package io.laplace.sprite.render;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Fabric 客户端 GameTest 调用计划（15.12 P0）。
 *
 * <p>只渲染调用形态，不执行客户端，也不声称客户端真的跑过。任务名取
 * {@value #TASK}；网络同步器报错时追加 {@value #SYNC_WORKAROUND_PROPERTY}。
 * Loom 的 {@code useXVFB} 是 {@code ClientProductionRunTask} 的任务属性而不是 CLI 参数，
 * 因此单独渲染成配置行，不伪造不存在的 gradle 参数。</p>
 */
public final class RenderTestPlan {

    public static final String TASK = "runProductionClientGameTest";

    public static final String SYNC_WORKAROUND_PROPERTY =
            "-Dfabric.client.gametest.disableNetworkSynchronizer=true";

    public static final String USE_XVFB_SETTING = "useXVFB = true";

    public static final String NETWORK_SYNC_ERROR_MARKER = "network synchronizer";

    /** 失败时上传的截图目录。 */
    public static final String SCREENSHOT_ARTIFACT_PATH = "build/run/clientGameTest/screenshots";

    private final String gradleExecutable;

    public RenderTestPlan() {
        this("./gradlew");
    }

    public RenderTestPlan(String gradleExecutable) {
        this.gradleExecutable = Objects.requireNonNull(gradleExecutable, "gradleExecutable");
    }

    /** 日志里出现网络同步器报错时需要兜底参数。 */
    public static boolean syncWorkaroundRequired(String launchLog) {
        return launchLog != null
                && launchLog.toLowerCase(Locale.ROOT).contains(NETWORK_SYNC_ERROR_MARKER);
    }

    /** 纯 Gradle 调用；XVFB 是任务属性，不在这里。 */
    public List<String> command(boolean syncWorkaround) {
        List<String> command = new ArrayList<>(4);
        command.add(gradleExecutable);
        command.add("--build-cache");
        if (syncWorkaround) {
            command.add(SYNC_WORKAROUND_PROPERTY);
        }
        command.add(TASK);
        return List.copyOf(command);
    }

    /**
     * 渲染完整调用计划：命令行 + （需要时）useXVFB 配置行。
     *
     * @param useXvfb        CI 无 X 显示时为 true
     * @param syncWorkaround 日志出现网络同步器报错时为 true
     */
    public List<String> render(boolean useXvfb, boolean syncWorkaround) {
        List<String> lines = new ArrayList<>(command(syncWorkaround));
        if (useXvfb) {
            lines.add(USE_XVFB_SETTING);
        }
        return List.copyOf(lines);
    }

    /** 单行文本，便于写进 CI 日志。 */
    public String renderLine(boolean useXvfb, boolean syncWorkaround) {
        return String.join(" ", render(useXvfb, syncWorkaround));
    }

    /** 截图证据目录，失败时整目录上传。 */
    public Path screenshotArtifactDir(Path projectDir) {
        return projectDir.resolve("build").resolve("run").resolve("clientGameTest").resolve("screenshots");
    }
}
