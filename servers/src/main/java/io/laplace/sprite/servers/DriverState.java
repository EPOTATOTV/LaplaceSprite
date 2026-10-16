package io.laplace.sprite.servers;

/**
 * 驱动器状态（12.2）。
 *
 * <p>{@link #UNAVAILABLE} 是「环境不具备」而不是失败：docker 不在、jar 没给，都不该阻断其余测试。</p>
 */
public enum DriverState {

    /** 环境不具备，未启动。 */
    UNAVAILABLE,
    /** 已知实例，未启动。 */
    STOPPED,
    /** 运行中。 */
    RUNNING,
    /** 启动失败。 */
    FAILED
}
