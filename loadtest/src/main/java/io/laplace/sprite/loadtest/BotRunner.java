package io.laplace.sprite.loadtest;

import java.util.List;

/**
 * bot 启动接缝（15.9）。
 *
 * <p>真实实现负责拉起进程或下发命令；测试注入内存替身，离线路径不产生任何真实会话。</p>
 */
public interface BotRunner {

    /** 按剧本拉起会话；返回实际建立的会话列表，环境不具备时返回空列表。 */
    List<BotSession> start(LoadScenario scenario);

    /** 结束会话；默认不做任何事，由具体实现覆盖。 */
    default void stop(List<BotSession> sessions) {
    }
}
