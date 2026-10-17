package io.laplace.sprite.loadtest;

import java.util.List;
import java.util.Objects;

/**
 * 声明式压测剧本（15.9）：并发数、爬坡曲线、动作类型、持续时长。
 *
 * @param id           剧本 id
 * @param backend      执行后端
 * @param actions      行为序列
 * @param durationTicks 持续 tick 数
 * @param ramp         爬坡曲线
 * @param note         说明；没有时为空串
 */
public record LoadScenario(String id, BotBackend backend, List<BotAction> actions, int durationTicks,
                           RampCurve ramp, String note) {

    public LoadScenario {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("剧本 id 不能为空");
        }
        backend = Objects.requireNonNull(backend, "backend");
        if (actions == null || actions.isEmpty()) {
            throw new IllegalArgumentException("剧本至少要声明一个动作: " + id);
        }
        actions = List.copyOf(actions);
        if (durationTicks <= 0) {
            throw new IllegalArgumentException("压测时长必须为正数: " + durationTicks);
        }
        ramp = Objects.requireNonNull(ramp, "ramp");
        note = note == null ? "" : note;
    }

    public List<RampCurve.RampStep> schedule() {
        return ramp.schedule(durationTicks);
    }

    /** 剧本声明的峰值并发。 */
    public int maxConcurrency() {
        return ramp.maxConcurrency();
    }
}
