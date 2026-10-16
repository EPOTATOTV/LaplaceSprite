package io.laplace.sprite.servers.clienttest;

import java.util.List;

/**
 * 客户端用例（15.7）：每个用例落在矩阵的一格（MC 版本 × 加载器 × JDK × OS）。
 *
 * @param id        用例 id
 * @param name      用例名
 * @param mcVersion Minecraft 版本
 * @param loader    加载器（fabric / forge / neoforge）
 * @param jdk       JDK 主版本
 * @param os        目标操作系统
 * @param steps     声明式步骤
 */
public record ClientTestCase(String id, String name, String mcVersion, String loader, String jdk, String os,
                             List<ClientDslStep> steps) {

    public ClientTestCase {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
