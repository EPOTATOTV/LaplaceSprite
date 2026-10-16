package io.laplace.sprite.simulator;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.PlatformKey;

import java.util.List;
import java.util.Random;

/**
 * 合法玩家行为模型（4.1 行为模型库）。
 *
 * <p>给定玩家、平台与种子，逐 tick 产出该行为下的事件序列。同一 seed 必须得到同样结果。</p>
 */
public interface BehaviorModel {

    BehaviorProfile profile();

    List<Event> generate(String playerId, PlatformKey platform, long startTick, Random rng, int ticks);
}
