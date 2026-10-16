package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.PlayerType;
import io.laplace.sprite.core.model.ProbeSnapshot;
import io.laplace.sprite.core.model.Scenario;

/**
 * 检测上下文（4.4）：喂给检测目标的一次调用所需的一切。
 */
public record DetectionContext(
        String runId,
        PlatformKey platform,
        Scenario scenario,
        EventStream stream,
        ProbeSnapshot probe,
        String playerId,
        PlayerType playerType,
        CheatSpec cheatSpec,
        CheatTierName tier) {
}
