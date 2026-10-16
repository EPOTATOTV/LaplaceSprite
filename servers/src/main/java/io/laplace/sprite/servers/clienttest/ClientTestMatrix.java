package io.laplace.sprite.servers.clienttest;

import java.util.ArrayList;
import java.util.List;

/**
 * 客户端测试矩阵（15.7）：MC 版本 × 加载器 × JDK × OS，按文档给的维度顺序嵌套展开。
 *
 * <p>XVFB 只在 CI 里自动启用（Linux runner 没有 X 显示）。环境变量取值可注入，因此这段判定能离线断言。</p>
 */
public final class ClientTestMatrix {

    /** 支持无头游戏测试的三个加载器。 */
    public static final List<String> DEFAULT_LOADERS = List.of("fabric", "forge", "neoforge");
    public static final List<String> DEFAULT_MC_VERSIONS = List.of("1.21.1", "1.20.6");
    public static final List<String> DEFAULT_JDKS = List.of("21", "17");
    public static final List<String> DEFAULT_OSES = List.of("linux", "windows", "macos");

    /** CI 环境变量名：任一存在即认为在 CI 中，自动启用 XVFB。 */
    public static final List<String> CI_ENV_NAMES = List.of("CI", "GITHUB_ACTIONS");

    /** 环境变量取值（默认 {@code System::getenv}），便于测试注入。 */
    public interface EnvLookup {
        String get(String name);
    }

    private final List<String> mcVersions;
    private final List<String> loaders;
    private final List<String> jdks;
    private final List<String> oses;
    private final EnvLookup env;

    public ClientTestMatrix(List<String> mcVersions, List<String> loaders, List<String> jdks, List<String> oses,
                            EnvLookup env) {
        this.mcVersions = List.copyOf(mcVersions);
        this.loaders = List.copyOf(loaders);
        this.jdks = List.copyOf(jdks);
        this.oses = List.copyOf(oses);
        this.env = env;
    }

    public static ClientTestMatrix defaults() {
        return new ClientTestMatrix(DEFAULT_MC_VERSIONS, DEFAULT_LOADERS, DEFAULT_JDKS, DEFAULT_OSES,
                System::getenv);
    }

    /** 按 MC → 加载器 → JDK → OS 的嵌套顺序展开全部组合。 */
    public List<ClientMatrixCell> combinations() {
        List<ClientMatrixCell> cells = new ArrayList<>(size());
        for (String mc : mcVersions) {
            for (String loader : loaders) {
                for (String jdk : jdks) {
                    for (String os : oses) {
                        cells.add(new ClientMatrixCell(mc, loader, jdk, os));
                    }
                }
            }
        }
        return List.copyOf(cells);
    }

    public int size() {
        return mcVersions.size() * loaders.size() * jdks.size() * oses.size();
    }

    /** CI 环境变量存在即为真；显式写成 false / 0 视为未启用。 */
    public boolean useXvfb() {
        for (String name : CI_ENV_NAMES) {
            String value = env.get(name);
            if (value != null && !value.isBlank() && !"false".equalsIgnoreCase(value) && !"0".equals(value)) {
                return true;
            }
        }
        return false;
    }
}
