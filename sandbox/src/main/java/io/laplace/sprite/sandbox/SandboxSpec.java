package io.laplace.sprite.sandbox;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 一次性沙箱用例定义（15.4）。
 *
 * <p>容器内存上限与 JVM 堆是两个量：容器吃 {@code --memory}，JVM 只拿约 70%，其余留给 metaspace、
 * 原生内存与线程栈。堆因此由 {@link #deriveHeapMb(int)} 派生而不是手填，声明与配额不会打架。</p>
 *
 * @param caseId               用例 id，兼作容器名与网络名后缀
 * @param image                镜像（itzg/docker-minecraft-server 镜像族）
 * @param serverType           镜像 TYPE 环境变量（PAPER / FABRIC / NEOFORGE ...）
 * @param serverVersion        镜像 VERSION 环境变量
 * @param memoryMb             容器内存上限（--memory）
 * @param cpus                 CPU 配额（--cpus）
 * @param jvmHeapMb            JVM 堆上限，由容器内存派生
 * @param readOnlyMounts       只读挂载：mods/ plugins/ 与服务端 jar
 * @param writableDataDir      唯一可写挂载的宿主目录（挂到 /data）
 * @param networkPolicy        本轮独立网络策略
 * @param rconPort             映射回宿主 127.0.0.1 的 RCON 端口
 * @param worldSnapshotEnabled 是否在用例前后做世界快照
 * @param oomRestartLimit      OOM 退避重启次数上限
 * @param extraEnv             追加的环境变量
 * @param labels               追加的容器 label
 */
public record SandboxSpec(String caseId, String image, String serverType, String serverVersion, int memoryMb,
                          double cpus, int jvmHeapMb, List<SandboxMount> readOnlyMounts, Path writableDataDir,
                          SandboxNetworkPolicy networkPolicy, int rconPort, boolean worldSnapshotEnabled,
                          int oomRestartLimit, Map<String, String> extraEnv, Map<String, String> labels) {

    /** 容器内可写目录，只此一处可写。 */
    public static final String CONTAINER_DATA_DIR = "/data";

    /** 堆占容器内存的比例：约 70%。 */
    public static final double HEAP_RATIO = 0.70;

    private static final Pattern CONTAINER_SAFE_ID = Pattern.compile("[a-z0-9][a-z0-9_.-]*");

    public SandboxSpec {
        if (caseId == null || !CONTAINER_SAFE_ID.matcher(caseId).matches()) {
            throw new IllegalArgumentException("caseId 必须匹配 [a-z0-9][a-z0-9_.-]*（同时用作容器名）: " + caseId);
        }
        requireText(image, "image");
        requireText(serverType, "serverType");
        requireText(serverVersion, "serverVersion");
        if (memoryMb <= 0) {
            throw new IllegalArgumentException("容器内存必须为正数: " + memoryMb);
        }
        if (cpus <= 0.0) {
            throw new IllegalArgumentException("CPU 配额必须为正数: " + cpus);
        }
        if (jvmHeapMb <= 0 || jvmHeapMb >= memoryMb) {
            throw new IllegalArgumentException("JVM 堆必须落在 (0, 容器内存) 区间: " + jvmHeapMb + " / " + memoryMb);
        }
        if (rconPort < 1024 || rconPort > 65535) {
            throw new IllegalArgumentException("RCON 端口越界: " + rconPort);
        }
        if (oomRestartLimit < 0) {
            throw new IllegalArgumentException("OOM 重启上限不能为负数: " + oomRestartLimit);
        }
        readOnlyMounts = readOnlyMounts == null ? List.of() : List.copyOf(readOnlyMounts);
        for (SandboxMount mount : readOnlyMounts) {
            if (!mount.readOnly()) {
                throw new IllegalArgumentException("readOnlyMounts 只接受只读挂载: " + mount.hostPath());
            }
            if (CONTAINER_DATA_DIR.equals(mount.containerPath())) {
                throw new IllegalArgumentException("/data 由 writableDataDir 独占，不能出现在 readOnlyMounts 里");
            }
        }
        writableDataDir = Objects.requireNonNull(writableDataDir, "writableDataDir");
        networkPolicy = Objects.requireNonNull(networkPolicy, "networkPolicy");
        extraEnv = extraEnv == null ? Map.of() : Map.copyOf(extraEnv);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    public static int deriveHeapMb(int memoryMb) {
        if (memoryMb <= 0) {
            throw new IllegalArgumentException("容器内存必须为正数: " + memoryMb);
        }
        return (int) Math.round(memoryMb * HEAP_RATIO);
    }

    /** 按等级配额装配 spec：内存 / CPU / OOM 重启上限一次性取齐，网络按用例隔离。 */
    public static SandboxSpec forTier(String caseId, SandboxTier tier, String image, String serverType,
                                      String serverVersion, List<SandboxMount> readOnlyMounts,
                                      Path writableDataDir, int rconPort) {
        return new SandboxSpec(caseId, image, serverType, serverVersion, tier.memoryMb(), tier.cpus(),
                deriveHeapMb(tier.memoryMb()), readOnlyMounts, writableDataDir,
                SandboxNetworkPolicy.isolate(caseId), rconPort, true, tier.oomRestartLimit(), Map.of(), Map.of());
    }

    /** itzg 镜像的 INIT_MEMORY：取堆上限的一半，避免起步就吃满配额。 */
    public int initHeapMb() {
        return Math.max(256, jvmHeapMb / 2);
    }

    /**
     * RCON 密码由 caseId 派生：沙箱网络不出宿主，密码只需与 {@link RconConsole} 共用同一套取值。
     */
    public String rconPassword() {
        return "lp-" + caseId + "-rcon";
    }

    public String containerName() {
        return "laplace-sandbox-" + caseId;
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " 不能为空");
        }
    }
}
