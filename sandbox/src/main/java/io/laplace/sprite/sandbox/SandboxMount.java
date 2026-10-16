package io.laplace.sprite.sandbox;

/**
 * 卷挂载声明（15.4 只读挂载）。
 *
 * @param hostPath      宿主机路径
 * @param containerPath 容器内路径
 * @param readOnly      是否只读挂载
 */
public record SandboxMount(String hostPath, String containerPath, boolean readOnly) {

    public SandboxMount {
        if (hostPath == null || hostPath.isBlank()) {
            throw new IllegalArgumentException("宿主路径不能为空");
        }
        if (containerPath == null || containerPath.isBlank()) {
            throw new IllegalArgumentException("容器路径不能为空");
        }
    }

    /** 只读挂载：mods/ plugins/ 与服务端 jar 都从这里进来。 */
    public static SandboxMount readOnly(String hostPath, String containerPath) {
        return new SandboxMount(hostPath, containerPath, true);
    }

    /** docker -v 参数：host:container[:ro]。 */
    public String volumeArgument() {
        return readOnly ? hostPath + ":" + containerPath + ":ro" : hostPath + ":" + containerPath;
    }
}
