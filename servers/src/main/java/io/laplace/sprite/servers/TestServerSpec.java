package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.PlatformKey;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 受测服务端实例定义（12.2 TestServerHost / 11.3 编排控制台的一行）。
 *
 * @param instanceId      实例 id（编排控制台与日志流的主键）
 * @param platform        目标平台
 * @param allocatedMemoryMb 分配堆内存
 * @param port            监听端口
 * @param eulaAccepted    是否已接受 EULA
 * @param headless        是否无头运行
 * @param dockerImage     容器镜像；本地进程轨可为空
 * @param jvmArgs         额外 JVM 参数
 * @param pluginVersion   被测插件/模组版本
 * @param mounts          目录绑定；纯 Bukkit 系 / 纯模组端为空，混合端见 {@link #hybrid}
 */
public record TestServerSpec(String instanceId, PlatformKey platform, int allocatedMemoryMb, int port,
                             boolean eulaAccepted, boolean headless, String dockerImage,
                             List<String> jvmArgs, String pluginVersion, List<ServerMount> mounts) {

    /** 混合端的插件目录挂载点。 */
    public static final String PLUGINS_CONTAINER_PATH = "/plugins";
    /** 混合端的模组目录挂载点。 */
    public static final String MODS_CONTAINER_PATH = "/mods";

    public TestServerSpec {
        jvmArgs = jvmArgs == null ? List.of() : List.copyOf(jvmArgs);
        mounts = mounts == null ? List.of() : List.copyOf(mounts);
    }

    /** 兼容旧调用：不带目录绑定。 */
    public TestServerSpec(String instanceId, PlatformKey platform, int allocatedMemoryMb, int port,
                          boolean eulaAccepted, boolean headless, String dockerImage,
                          List<String> jvmArgs, String pluginVersion) {
        this(instanceId, platform, allocatedMemoryMb, port, eulaAccepted, headless, dockerImage, jvmArgs,
                pluginVersion, List.of());
    }

    /** 开发轨默认实例：容器镜像取官方 Java 21 服务端镜像，端口用默认值。 */
    public static TestServerSpec inProcess(String instanceId, PlatformKey platform, String pluginVersion) {
        return new TestServerSpec(instanceId, platform, 1024, 25565, true, true,
                "itzg/minecraft-server:java21", List.of(), pluginVersion);
    }

    /**
     * 混合端实例（v1.1.0 §14.3：Arclight / Youer）：Bukkit 插件目录与模组加载器目录同时存在。
     *
     * <p>两个目录都按只读绑定给出，沙箱或真实环境驱动直接取 {@link #mounts()} 挂载即可。</p>
     */
    public static TestServerSpec hybrid(String instanceId, PlatformKey platform, String pluginVersion,
                                        Path pluginsDir, Path modsDir) {
        if (!platform.family().isHybrid()) {
            throw new IllegalArgumentException("hybrid 只用于混合端家族，收到: " + platform.family());
        }
        return new TestServerSpec(instanceId, platform, 2048, 25565, true, true,
                "itzg/minecraft-server:java21", List.of(), pluginVersion,
                List.of(ServerMount.readOnly(pluginsDir.toString(), PLUGINS_CONTAINER_PATH),
                        ServerMount.readOnly(modsDir.toString(), MODS_CONTAINER_PATH)));
    }

    /** 启动命令里的完整 JVM 参数：先内存，再附加上参数。 */
    public List<String> jvmArgsWithMemory() {
        List<String> args = new ArrayList<>();
        args.add("-Xmx%dM".formatted(allocatedMemoryMb));
        args.add("-Xms%dM".formatted(Math.max(256, allocatedMemoryMb / 2)));
        args.addAll(jvmArgs);
        return List.copyOf(args);
    }
}
