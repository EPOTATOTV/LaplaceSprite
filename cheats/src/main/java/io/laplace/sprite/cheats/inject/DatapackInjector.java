package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;

import java.time.Instant;
import java.util.List;

/**
 * DATAPACK：pack_format 失配与函数递归（升级实施文档 L-02）。
 *
 * <p>数据包问题发生在加载期，事件流里用 {@link Event.DataConsistency} 记录 pack.mcmeta 的版本判定，
 * 函数级问题用 {@link Event.GameTestSuite} 的失败项表达，不伪装成玩家行为。</p>
 */
public final class DatapackInjector extends AbstractSampleInjector {

    @Override
    public String id() {
        return "datapack";
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        emitAll(out, baseline);
        Instant origin = originOf(request.baseline());
        long tick = lastTick(baseline) + 1;
        switch (request.sample().sampleType()) {
            case "PACK_FORMAT_MISMATCH" -> {
                int declared = paramInt(tier, "declaredFormat", 81);
                int target = paramInt(tier, "targetFormat", 86);
                out.emit(new Event.DataConsistency(request.playerId(), tick, timestampAt(tick, origin),
                        request.platform(), "datapack:pack_format",
                        declared + "->" + target, declared == target ? "match" : "mismatch"));
            }
            case "FUNCTION_RECURSION" -> {
                int depth = paramInt(tier, "depth", 8);
                int tickRepeat = paramInt(tier, "tickRepeat", 4);
                out.emit(new Event.GameTestSuite(request.playerId(), tick, timestampAt(tick, origin),
                        request.platform(), "datapack", "function-recursion", false,
                        "depth=" + depth + " tickRepeat=" + tickRepeat));
            }
            default -> out.emit(new Event.DataConsistency(request.playerId(), tick, timestampAt(tick, origin),
                    request.platform(), "datapack.sample", request.sample().sampleType(), "unhandled"));
        }
    }
}
