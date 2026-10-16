package io.laplace.sprite.servers;

/**
 * 目录绑定（12.1 真实环境轨 / 15.4 只读挂载）：混合端要同时把 plugins/ 与 mods/ 挂进去。
 *
 * @param hostPath      宿主机目录
 * @param containerPath 容器内挂载点
 * @param readOnly      是否只读挂载
 */
public record ServerMount(String hostPath, String containerPath, boolean readOnly) {

    /** 只读挂载：被测产物只从外部进来，不许被测试代码写回。 */
    public static ServerMount readOnly(String hostPath, String containerPath) {
        return new ServerMount(hostPath, containerPath, true);
    }

    /** docker -v 参数：host:container[:ro]。 */
    public String volumeArgument() {
        return readOnly ? hostPath + ":" + containerPath + ":ro" : hostPath + ":" + containerPath;
    }
}
