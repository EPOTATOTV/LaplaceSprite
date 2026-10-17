package io.laplace.sprite.replay;

import java.util.List;
import java.util.Optional;

/**
 * 玩家移动轨迹（15.10）：按 tick 去重后的位姿序列。
 *
 * @param samples 按 tick 升序的样本
 */
public record PlayerMovementTrack(List<MovementSample> samples) {

    public PlayerMovementTrack {
        samples = samples == null ? List.of() : List.copyOf(samples);
    }

    public boolean isEmpty() {
        return samples.isEmpty();
    }

    public int size() {
        return samples.size();
    }

    public Optional<MovementSample> last() {
        return samples.isEmpty() ? Optional.empty() : Optional.of(samples.get(samples.size() - 1));
    }

    /** 最后一个样本的 tick；空轨迹为 0。 */
    public int lastTick() {
        return samples.isEmpty() ? 0 : samples.get(samples.size() - 1).tick();
    }
}
