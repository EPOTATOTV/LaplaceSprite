package io.laplace.sprite.servers.clienttest;

import java.util.ArrayList;
import java.util.List;

/**
 * Loom 任务渲染（15.7）：只产出 {@code runClientGameTest} 的调用形态，不真的跑客户端。
 *
 * <p>网络同步器报错时追加 {@value #SYNC_WORKAROUND_PROPERTY}；Linux runner 没有 X 显示时用 xvfb-run 包一层。</p>
 */
public final class LoomTaskRenderer {

    public static final String TASK = "runClientGameTest";
    public static final String SYNC_WORKAROUND_PROPERTY =
            "-Dfabric.client.gametest.disableNetworkSynchronizer=true";
    public static final String XVFB_EXECUTABLE = "xvfb-run";
    /** xvfb-run 的虚拟屏参数：1280x720、24 位色深。 */
    public static final String XVFB_SCREEN = "-screen 0 1280x720x24";

    private final String gradleExecutable;

    public LoomTaskRenderer() {
        this("./gradlew");
    }

    public LoomTaskRenderer(String gradleExecutable) {
        this.gradleExecutable = gradleExecutable;
    }

    /**
     * 渲染调用参数向量。
     *
     * @param syncWorkaround 日志中出现网络同步器报错时为 true
     * @param useXvfb        CI（Linux）无 X 显示时为 true
     */
    public List<String> render(boolean syncWorkaround, boolean useXvfb) {
        List<String> command = new ArrayList<>();
        command.add(gradleExecutable);
        // 矩阵里每个格子都要构建一次，构建缓存必须带上
        command.add("--build-cache");
        if (syncWorkaround) {
            command.add(SYNC_WORKAROUND_PROPERTY);
        }
        command.add(TASK);
        if (!useXvfb) {
            return List.copyOf(command);
        }
        List<String> wrapped = new ArrayList<>();
        wrapped.add(XVFB_EXECUTABLE);
        wrapped.add("-a");
        wrapped.add("-s");
        wrapped.add(XVFB_SCREEN);
        wrapped.addAll(command);
        return List.copyOf(wrapped);
    }

    /** 单行形式，便于写进 CI job 或日志。 */
    public String renderLine(boolean syncWorkaround, boolean useXvfb) {
        return String.join(" ", render(syncWorkaround, useXvfb));
    }
}
