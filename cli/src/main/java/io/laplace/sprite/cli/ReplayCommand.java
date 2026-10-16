package io.laplace.sprite.cli;

import io.laplace.sprite.adapters.sim.SimAdapter;
import io.laplace.sprite.core.engine.DetectionContext;
import io.laplace.sprite.core.engine.InMemoryEventStream;
import io.laplace.sprite.core.event.Event;
import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.event.EventType;
import io.laplace.sprite.core.model.BehaviorProfile;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.FlagEvent;
import io.laplace.sprite.core.model.LegitTrajectory;
import io.laplace.sprite.core.model.NetworkModel;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.model.PlayerType;
import io.laplace.sprite.core.model.ProbeSnapshot;
import io.laplace.sprite.core.model.Scenario;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * {@code laplace replay}：按 seed 复现一条轨迹（4.6 可回放），并用当前检测目标重跑一次。
 *
 * <p>轨迹由 seed 与行为模型唯一决定，因此「同 seed 同结果」本身就是可复现性证据。</p>
 */
@Command(name = "replay", description = "按 seed 回放轨迹并重跑检测（合法轨迹 / 作弊注入轨迹）",
        mixinStandardHelpOptions = true)
final class ReplayCommand implements Callable<Integer> {

    @Option(names = "--scenario", defaultValue = "pvp", description = "场景剧本 id")
    String scenarioId;

    @Option(names = "--profile", defaultValue = "SPRINT", description = "行为模型（合法回放）")
    BehaviorProfile profile;

    @Option(names = "--seed", defaultValue = "20261013", description = "随机种子")
    long seed;

    @Option(names = "--ticks", defaultValue = "400", description = "tick 数")
    int ticks;

    @Option(names = "--cheat", description = "按作弊样本 id 回放注入轨迹")
    String cheatId;

    @Option(names = "--tier", defaultValue = "DEFAULT", description = "作弊档位")
    CheatTierName tier;

    @Option(names = "--platform", defaultValue = "in-process", description = "目标平台")
    String platform;

    @Option(names = "--mc", defaultValue = Support.DEFAULT_MC, description = "Minecraft 版本")
    String mc;

    @Option(names = "--adapter", description = "加载器版本")
    String loader;

    @Option(names = "--stepwise", description = "逐条打印事件流头部")
    boolean stepwise;

    @Option(names = "--limit", defaultValue = "40", description = "--stepwise 打印条数上限")
    int limit;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        SuiteFactory factory = new SuiteFactory();
        Scenario scenario = factory.scenario(scenarioId);
        PlatformKey key = Support.resolvePlatform(spec, platform, mc, loader);
        var out = spec.commandLine().getOut();

        EventStream stream;
        PlayerType playerType;
        CheatSpec spec = null;
        if (cheatId != null && !cheatId.isBlank()) {
            List<CheatSpec> specs = factory.specs(List.of(cheatId));
            spec = specs.get(0);
            NetworkModel network = factory.network(scenario, seed);
            EventStream baseline = factory.baseline(scenario, key, seed, ticks);
            var sessions = factory.cheat(specs, tier, scenario, key, network, seed, baseline);
            if (sessions.isEmpty()) {
                out.printf("作弊样本 %s 未声明档位 %s，无可回放内容%n", cheatId, tier);
                return 1;
            }
            stream = sessions.get(0).stream();
            playerType = PlayerType.CHEAT;
        } else {
            LegitTrajectory trajectory = factory.trajectory("replay", profile, scenario, seed, ticks, key);
            stream = new InMemoryEventStream(trajectory.events());
            playerType = PlayerType.LEGIT;
        }

        out.printf("回放 %s：场景 %s · 平台 %s · seed %d · tick %d%n",
                spec == null ? profile : spec.id() + "/" + tier,
                scenario.id(), key == null ? "in-process" : key.id(), seed, ticks);
        out.printf("  事件 %d 条 · 类型分布 %s%n", stream.size(), histogram(stream));

        if (stepwise) {
            out.println("  事件流头部：");
            stream.events().stream().limit(limit).forEach(e -> out.println("    " + describe(e)));
        }

        DetectionContext ctx = new DetectionContext("replay-" + seed, key, scenario, stream,
                ProbeSnapshot.clean(), "replay", playerType, spec, spec == null ? null : tier);
        List<FlagEvent> flags = new SimAdapter().detect(ctx);
        if (flags.isEmpty()) {
            out.println("  检测结果：无 flag（与预期一致）");
            return 0;
        }
        out.printf("  检测结果：%d 条 flag%n", flags.size());
        flags.forEach(f -> out.printf("    - %s @tick %d 置信度 %.2f%n", f.checkName(), f.tick(),
                f.confidence()));
        return spec == null ? 1 : 0;
    }

    private static Map<EventType, Integer> histogram(EventStream stream) {
        Map<EventType, Integer> map = new EnumMap<>(EventType.class);
        stream.events().forEach(e -> map.merge(e.type(), 1, Integer::sum));
        return map;
    }

    private static String describe(Event event) {
        return "t=" + event.tick() + " " + event.type() + " " + event.playerId();
    }
}
