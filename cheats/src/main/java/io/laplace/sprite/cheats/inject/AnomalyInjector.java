package io.laplace.sprite.cheats.inject;

import io.laplace.sprite.cheats.CheatInjectionRequest;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.model.SampleTier;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * ANOMALY：无效输入、越界参数、并发竞争、资源引用缺失与故障注入。
 *
 * <p>这些是「输入契约」层面的异常，不伪装成玩家行为；NaN 与越界值直接写进 Move，
 * 用来验证校验发生在写入世界状态之前。</p>
 */
public final class AnomalyInjector extends AbstractSampleInjector {

    @Override
    public String id() {
        return "anomaly";
    }

    @Override
    protected void apply(List<Event> baseline, InMemoryEventStream out, CheatInjectionRequest request,
                         SampleTier tier) {
        Instant origin = originOf(request.baseline());
        long tick = lastTick(baseline) + 1;
        String player = request.playerId();
        switch (request.sample().sampleType()) {
            case "INVALID_INPUT_NAN_COORDINATES" -> emitAll(out, nanCoordinates(baseline, tier));
            case "OUT_OF_RANGE_PARAMETER" -> emitAll(out, outOfRange(baseline, request, tier, tick, origin));
            case "CONCURRENT_RACE" -> emitAll(out, concurrentRace(baseline, request, tier, tick, origin));
            case "MISSING_RESOURCE_REFERENCE" -> emitAll(out,
                    missingResource(baseline, request, tier, tick, origin));
            case "FAULT_INJECTION" -> emitAll(out, faultInjection(baseline, request, tier, origin));
            default -> {
                emitAll(out, baseline);
                out.emit(new Event.DataConsistency(player, tick, timestampAt(tick, origin),
                        request.platform(), "anomaly.sample", request.sample().sampleType(), "unhandled"));
            }
        }
    }

    private static List<Event> nanCoordinates(List<Event> baseline, SampleTier tier) {
        int corruptEvery = Math.max(1, paramInt(tier, "corruptEvery", 8));
        List<Event> events = new ArrayList<>(baseline.size());
        int moveIndex = 0;
        for (Event event : baseline) {
            if (event instanceof Event.Move move && moveIndex++ % corruptEvery == 0) {
                events.add(new Event.Move(move.playerId(), move.tick(), move.timestamp(), move.platform(),
                        Double.NaN, move.y(), Double.NaN, move.yaw(), move.pitch(), move.onGround(),
                        move.sprinting(), move.sneaking()));
            } else {
                events.add(event);
            }
        }
        return events;
    }

    private static List<Event> outOfRange(List<Event> baseline, CheatInjectionRequest request, SampleTier tier,
                                          long tick, Instant origin) {
        int atTick = paramInt(tier, "atTick", 15);
        double scale = paramDouble(tier, "coordinateScale", 10_000_000.0);
        float pitch = (float) paramDouble(tier, "pitch", 120.0);
        List<Event> events = new ArrayList<>(baseline.size() + 1);
        boolean replaced = false;
        for (Event event : baseline) {
            if (!replaced && event instanceof Event.Move move && event.tick() >= atTick) {
                events.add(new Event.Move(move.playerId(), move.tick(), move.timestamp(), move.platform(),
                        scale, 1_000_000.0, -scale, move.yaw(), pitch, move.onGround(),
                        move.sprinting(), move.sneaking()));
                replaced = true;
            } else {
                events.add(event);
            }
        }
        if (!replaced) {
            events.add(new Event.Move(request.playerId(), tick, timestampAt(tick, origin), request.platform(),
                    scale, 1_000_000.0, -scale, 0f, pitch, false, false, false));
        }
        return events;
    }

    private static List<Event> concurrentRace(List<Event> baseline, CheatInjectionRequest request, SampleTier tier,
                                              long tick, Instant origin) {
        int races = Math.max(1, paramInt(tier, "raceCount", 2));
        List<Event> events = new ArrayList<>(baseline);
        for (int i = 0; i < races; i++) {
            long at = tick + i;
            Instant timestamp = timestampAt(at, origin);
            events.add(new Event.Inventory(request.playerId(), at, timestamp, request.platform(),
                    "hotbar.0", "minecraft:diamond_sword", 1));
            events.add(new Event.Inventory(request.playerId(), at, timestamp, request.platform(),
                    "hotbar.0", "minecraft:shield", 1));
            events.add(new Event.Move(request.playerId(), at, timestamp, request.platform(),
                    0.5 + i, 64.0, 0.5, 0f, 0f, true, false, false));
            events.add(new Event.Move(request.playerId(), at, timestamp, request.platform(),
                    -0.5 - i, 64.0, -0.5, 0f, 0f, true, false, false));
        }
        return events;
    }

    private static List<Event> missingResource(List<Event> baseline, CheatInjectionRequest request,
                                               SampleTier tier, long tick, Instant origin) {
        int missing = Math.max(1, paramInt(tier, "missingRefs", 2));
        String namespace = paramString(tier, "namespace", "laplace");
        List<Event> events = new ArrayList<>(baseline);
        for (int i = 0; i < missing; i++) {
            String id = namespace + ":missing_model_" + i;
            events.add(new Event.Registry(request.playerId(), tick, timestampAt(tick, origin),
                    request.platform(), "minecraft:model", id, "RESOLVE"));
            events.add(new Event.DataConsistency(request.playerId(), tick, timestampAt(tick, origin),
                    request.platform(), "resource:model:" + id, id, "absent"));
        }
        return events;
    }

    private static List<Event> faultInjection(List<Event> baseline, CheatInjectionRequest request, SampleTier tier,
                                              Instant origin) {
        int faultTick = paramInt(tier, "faultTick", 15);
        List<Event> events = new ArrayList<>(baseline.size() + 2);
        int insertAt = baseline.size();
        for (int i = 0; i < baseline.size(); i++) {
            if (baseline.get(i).tick() >= faultTick) {
                insertAt = i;
                break;
            }
        }
        events.addAll(baseline.subList(0, insertAt));
        events.add(new Event.Disconnect(request.playerId(), faultTick,
                timestampAt(faultTick, origin), request.platform(), "injected_fault"));
        events.addAll(baseline.subList(insertAt, baseline.size()));
        long endTick = lastTick(baseline) + 1;
        events.add(new Event.GameTestSuite(request.playerId(), endTick, timestampAt(endTick, origin),
                request.platform(), "anomaly", "fault-injection", false, "injected fault mid-stream"));
        return events;
    }
}
