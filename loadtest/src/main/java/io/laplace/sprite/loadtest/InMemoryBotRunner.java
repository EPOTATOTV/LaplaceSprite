package io.laplace.sprite.loadtest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 内存 bot 启动器，仅供测试与离线演示。
 *
 * <p>返回的会话全部标记 {@code live=false}，不对应任何真实连接；用来验证并发数、
 * 剧本传递与后续指标装配，不伪造在线状态。</p>
 */
public final class InMemoryBotRunner implements BotRunner {

    private final List<LoadScenario> started = new CopyOnWriteArrayList<>();
    private final List<List<BotSession>> batches = new CopyOnWriteArrayList<>();

    @Override
    public List<BotSession> start(LoadScenario scenario) {
        started.add(scenario);
        List<BotSession> sessions = new ArrayList<>(scenario.maxConcurrency());
        for (int index = 1; index <= scenario.maxConcurrency(); index++) {
            sessions.add(new BotSession("bot-" + index, scenario.backend(), false,
                    "内存替身，未建立真实连接"));
        }
        List<BotSession> result = List.copyOf(sessions);
        batches.add(result);
        return result;
    }

    public List<LoadScenario> started() {
        return List.copyOf(started);
    }

    public List<List<BotSession>> batches() {
        return List.copyOf(batches);
    }
}
