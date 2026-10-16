package io.laplace.sprite.servers;

import io.laplace.sprite.core.model.MetricSample;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 阶梯负载（12.3 ②）：25 / 50 / 100 模拟玩家逐级加压，找第一个性能拐点。
 *
 * <p>{@link #knee()} 只看已经测过的台阶，且按台阶顺序取第一个越界的那个。</p>
 */
public final class LoadStaircase {

    /** 默认台阶。 */
    public static final List<Integer> DEFAULT_PLAYER_STEPS = List.of(25, 50, 100);

    private final List<Integer> playerSteps;
    private final Map<Integer, LoadStage> measured = new LinkedHashMap<>();

    public LoadStaircase() {
        this(DEFAULT_PLAYER_STEPS);
    }

    public LoadStaircase(List<Integer> playerSteps) {
        this.playerSteps = List.copyOf(playerSteps);
    }

    public List<Integer> playerSteps() {
        return playerSteps;
    }

    /** 台阶计划：已测台阶给实测值，未测台阶给标称值。 */
    public List<LoadStage> plan() {
        List<LoadStage> stages = new ArrayList<>(playerSteps.size());
        for (int players : playerSteps) {
            stages.add(measured.getOrDefault(players, LoadStage.planned(players)));
        }
        return List.copyOf(stages);
    }

    public void record(int simulatedPlayers, MetricSample sample) {
        measured.put(simulatedPlayers, LoadStage.of(simulatedPlayers, sample));
    }

    /** 第一个越界台阶。 */
    public Optional<LoadStage> knee() {
        for (int players : playerSteps) {
            LoadStage stage = measured.get(players);
            if (stage != null && stage.breached()) {
                return Optional.of(stage);
            }
        }
        return Optional.empty();
    }

    public List<LoadStage> measuredStages() {
        List<LoadStage> stages = new ArrayList<>(measured.values());
        return List.copyOf(stages);
    }

    public boolean completed() {
        return measured.keySet().containsAll(playerSteps);
    }

    /** 是否在某个台阶上出现拐点。 */
    public boolean hasKnee() {
        return knee().isPresent();
    }
}
