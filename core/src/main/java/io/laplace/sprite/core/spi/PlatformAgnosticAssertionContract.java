package io.laplace.sprite.core.spi;

import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.AssertionResult;
import io.laplace.sprite.core.model.AssertionStatus;
import io.laplace.sprite.core.model.Scenario;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台无关的标准断言契约（14.4 ⑤ / 14.6）。
 *
 * <p>只读内部事件流，不做平台判断；平台差异全部由适配器在桥内收敛。</p>
 */
public final class PlatformAgnosticAssertionContract implements AssertionContract {

    /** 一个场景至少要归一出的事件类型数（14.8 验收：≥10 类核心事件归一）。 */
    private static final int MIN_CORE_EVENT_TYPES = 10;

    /** 核心事件类型白名单，覆盖 14.6 归一化模型的必测集合。 */
    private static final List<String> CORE_EVENT_TYPES = List.of(
            "PLAYER_JOIN", "PLAYER_LEAVE", "MOVE", "INTERACT", "CHAT", "INVENTORY",
            "PACKET", "CHUNK_LOAD", "BLOCK_CHANGE", "DATA_CONSISTENCY",
            "PROXY_PLAYER_JOIN", "PROXY_PLAYER_LEAVE", "PROXY_CHAT", "DISCONNECT",
            "COMBAT", "BRAND", "MOD_HASH", "ADDON", "REGISTRY", "GAME_TEST_SUITE");

    @Override
    public List<AssertionResult> run(Scenario scenario, EventStream stream) {
        List<AssertionResult> results = new ArrayList<>();
        results.add(assertStreamCarriesEvents(scenario, stream));
        results.add(assertCoreEventCoverage(stream));
        results.add(assertPlayersRegistered(scenario, stream));
        return results;
    }

    private AssertionResult assertStreamCarriesEvents(Scenario scenario, EventStream stream) {
        if (stream.isEmpty()) {
            return AssertionResult.failed("stream-non-empty",
                    "场景「%s」未产出任何归一化事件".formatted(scenario.id()));
        }
        return AssertionResult.passed("stream-non-empty",
                "场景「%s」归一化事件 %d 条".formatted(scenario.id(), stream.size()));
    }

    private AssertionResult assertCoreEventCoverage(EventStream stream) {
        long covered = CORE_EVENT_TYPES.stream()
                .filter(t -> !stream.ofType(io.laplace.sprite.core.event.EventType.valueOf(t)).isEmpty())
                .count();
        if (covered < MIN_CORE_EVENT_TYPES) {
            return AssertionResult.failed("core-event-bridge-coverage",
                    "核心事件归一覆盖 %d 类，低于要求 %d 类".formatted(covered, MIN_CORE_EVENT_TYPES));
        }
        return AssertionResult.passed("core-event-bridge-coverage",
                "核心事件归一覆盖 %d 类".formatted(covered));
    }

    private AssertionResult assertPlayersRegistered(Scenario scenario, EventStream stream) {
        long distinctPlayers = stream.stream().map(Event::playerId).distinct().count();
        if (distinctPlayers == 0) {
            return new AssertionResult("players-present", AssertionStatus.SKIPPED, "无玩家事件", java.util.Map.of());
        }
        if (distinctPlayers > scenario.simulatedPlayers()) {
            return AssertionResult.failed("players-present",
                    "事件流玩家数 %d 超过场景声明 %d".formatted(distinctPlayers, scenario.simulatedPlayers()));
        }
        return AssertionResult.passed("players-present", "事件流玩家数 %d".formatted(distinctPlayers));
    }
}
