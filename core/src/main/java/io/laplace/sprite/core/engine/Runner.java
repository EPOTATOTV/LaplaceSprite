package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.DetectionBreakdown;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.MetricSet;
import io.laplace.sprite.core.model.PlayerType;
import io.laplace.sprite.core.model.RunStatus;
import io.laplace.sprite.core.model.Scenario;
import io.laplace.sprite.core.model.TestRun;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 运行器（4.4 执行层 / 11.1 渲染分层）：把模拟会话灌给检测目标，产出一次 TestRun。
 *
 * <p>只做「喂事件 → 收 flag → 分类统计」，不感知平台与作弊类型细节；注入由 cheats 模块完成，
 * 断言由判定层完成。</p>
 */
public final class Runner {

    public TestRun execute(RunRequest request, Scenario scenario, List<SimulatedSession> sessions,
                           DetectionTarget target) {
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();

        List<FlagEvent> flags = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger legitPlayers = new AtomicInteger();
        AtomicInteger cheatSamples = new AtomicInteger();
        AtomicInteger detectedCheat = new AtomicInteger();
        AtomicInteger missed = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();
        AtomicInteger boundaryTotal = new AtomicInteger();
        AtomicInteger boundaryPassed = new AtomicInteger();
        AtomicBoolean concurrencyOk = new AtomicBoolean(true);

        Map<String, Map<CheatTierName, Integer>> hitsByCheckTier = new LinkedHashMap<>();
        Map<CheatTierName, Integer> samplesByTier = new EnumMap<>(CheatTierName.class);
        Map<CheatType, Integer> hitsByCheatType = new EnumMap<>(CheatType.class);
        Map<CheatType, Integer> samplesByCheatType = new EnumMap<>(CheatType.class);
        Object breakdownLock = new Object();

        var task = (java.util.function.Consumer<SimulatedSession>) session -> {
            try {
                if (session.playerType() == PlayerType.LEGIT) {
                    legitPlayers.incrementAndGet();
                }
                boolean cheat = session.isCheat();
                if (cheat) {
                    cheatSamples.incrementAndGet();
                    synchronized (breakdownLock) {
                        samplesByTier.merge(session.tier(), 1, Integer::sum);
                        samplesByCheatType.merge(session.cheatSpec().type(), 1, Integer::sum);
                    }
                }
                if (session.isBoundary()) {
                    boundaryTotal.incrementAndGet();
                }

                DetectionContext ctx = new DetectionContext(request.runId(), request.platform(),
                        scenario, session.stream(), session.probe(), session.playerId(),
                        session.playerType(), session.cheatSpec(), session.tier());

                List<FlagEvent> raw = target.detect(ctx);
                boolean hit = raw != null && !raw.isEmpty();

                if (!cheat) {
                    if (hit) {
                        for (FlagEvent f : raw) {
                            // 4.1 豁免规则：创造 / 旁观 / 允许飞行 / 开局缓冲 / 插件传送与击退
                            // 内的触发不计误报，但仍以 exempted 形式进证据链。
                            var rule = request.exemptions().exemptionFor(session, f.tick());
                            if (rule.isPresent()) {
                                flags.add(FlagEvent.exempted(request.runId(), session.playerId(),
                                        f.checkName(), f.tick(), session.trajectoryRef(),
                                        f.evidenceRef(), rule.get()));
                            } else {
                                flags.add(FlagEvent.falsePositive(request.runId(), session.playerId(),
                                        f.checkName(), f.tick(), session.trajectoryRef(), f.evidenceRef()));
                            }
                        }
                    }
                } else if (hit) {
                    detectedCheat.incrementAndGet();
                    for (FlagEvent f : raw) {
                        flags.add(FlagEvent.truePositive(request.runId(), session.playerId(),
                                f.checkName(), session.cheatSpec().type(), session.tier(),
                                f.confidence(), f.tick(), session.trajectoryRef(), f.evidenceRef()));
                        synchronized (breakdownLock) {
                            hitsByCheckTier
                                    .computeIfAbsent(f.checkName(), k -> new EnumMap<>(CheatTierName.class))
                                    .merge(session.tier(), 1, Integer::sum);
                            hitsByCheatType.merge(session.cheatSpec().type(), 1, Integer::sum);
                        }
                    }
                }

                boolean expectedDetection = session.expected() != null && session.expected().expectedDetected();
                if (cheat && expectedDetection && !hit) {
                    missed.incrementAndGet();
                }
                if (cheat && !expectedDetection && hit) {
                    unexpected.incrementAndGet();
                }
                if (session.isBoundary() && hit == expectedDetection) {
                    boundaryPassed.incrementAndGet();
                }
            } catch (RuntimeException ex) {
                concurrencyOk.set(false);
                throw ex;
            }
        };

        if (request.parallel()) {
            sessions.parallelStream().forEach(task);
        } else {
            sessions.forEach(task);
        }

        int falsePositives = (int) flags.stream().filter(FlagEvent::falsePositive).count();
        MetricSet metrics = new MetricSet(legitPlayers.get(), falsePositives, cheatSamples.get(),
                detectedCheat.get(), boundaryTotal.get(), boundaryPassed.get(), concurrencyOk.get(),
                request.coverage());

        DetectionBreakdown breakdown = new DetectionBreakdown(hitsByCheckTier, samplesByTier,
                hitsByCheatType, samplesByCheatType);

        boolean passed = falsePositives == 0 && missed.get() == 0 && unexpected.get() == 0
                && boundaryPassed.get() == boundaryTotal.get() && concurrencyOk.get();

        long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();

        return new TestRun(request.runId(), request.targetId(), request.suite(), request.platform(),
                metrics, breakdown, List.of(), List.copyOf(flags),
                passed ? RunStatus.PASSED : RunStatus.FAILED, request.seed(), startedAt, durationMs);
    }
}
