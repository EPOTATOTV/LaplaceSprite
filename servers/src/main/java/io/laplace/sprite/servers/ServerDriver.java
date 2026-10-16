package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.RunStatus;

import java.util.List;

/**
 * 服务端驱动器（12.2 / 12.1 环境三轨）。
 *
 * <p>实现分别是容器轨（docker compose）、开发轨（进程内）、本地进程轨（真实 jar）。
 * 三轨的差异全部收敛在实现里，编排控制台只认这个接口。</p>
 */
public interface ServerDriver {

    void start(TestServerSpec spec);

    void stop(String instanceId);

    boolean running(String instanceId);

    /** 尾部日志。 */
    List<String> tail(String instanceId, int lines);

    RunStatus status(String instanceId);

    default String name() {
        return getClass().getSimpleName();
    }

    default DriverState state(String instanceId) {
        return running(instanceId) ? DriverState.RUNNING : DriverState.STOPPED;
    }

    /** 环境是否具备（docker 可用 / jar 存在 / 进程内恒为真）。 */
    default boolean available() {
        return true;
    }
}
