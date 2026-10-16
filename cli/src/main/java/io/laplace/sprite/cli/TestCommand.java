package io.laplace.sprite.cli;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterRegistryFactory;
import io.laplace.sprite.adapters.SmokeScenarios;
import io.laplace.sprite.core.model.PlatformKey;
import io.laplace.sprite.core.spi.AdapterRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace test}：适配器矩阵冒烟（14.8 六步中的第 3 步）。
 *
 * <p>跑三个冒烟剧本：装配成功 / 核心事件归一 / 生命周期全链路。CI 矩阵按
 * {@code laplace test --platform ... --mc ... --adapter ...} 调用本命令。</p>
 */
@Command(name = "test", description = "适配器矩阵冒烟：装配成功 / 核心事件归一 / 生命周期全链路",
        mixinStandardHelpOptions = true)
final class TestCommand implements Callable<Integer> {

    @Option(names = "--platform", defaultValue = "paper", description = "目标平台")
    String platform;

    @Option(names = "--mc", defaultValue = Support.DEFAULT_MC, description = "Minecraft 版本")
    String mc;

    @Option(names = "--adapter", description = "加载器版本（模组线）")
    String loader;

    @Option(names = "--all", description = "跑全部 17 个适配器")
    boolean all;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        AdapterRegistry registry = AdapterRegistryFactory.defaultRegistry();
        var out = spec.commandLine().getOut();
        SmokeScenarios smoke = new SmokeScenarios();
        List<SmokeScenarios.SmokeResult> results = new ArrayList<>();

        if (all) {
            PlatformKey key = new PlatformKey(io.laplace.sprite.core.model.PlatformFamily.PAPER, mc, null);
            for (AbstractLaplaceAdapter adapter : AdapterRegistryFactory.allAdapters()) {
                PlatformKey adapterKey = keyOf(adapter, mc, loader);
                results.addAll(smoke.run(adapter, adapterKey));
            }
        } else {
            PlatformKey key = Support.requirePlatform(spec, platform, mc, loader);
            var adapter = registry.resolve(key);
            if (adapter.isEmpty()) {
                out.printf("矩阵无可用适配器: %s%n", key.id());
                return 1;
            }
            results.addAll(smoke.run(adapter.get(), key));
        }

        long failed = results.stream().filter(r -> !r.passed()).count();
        results.forEach(r -> out.printf("  [%s] %s / %s — %s%n", r.passed() ? "PASS" : "FAIL",
                r.adapterId(), r.scenario(), r.detail()));
        registry.warnings().forEach(w -> out.println("  告警：" + w));
        out.printf("冒烟结果：%d 项，失败 %d 项%n", results.size(), failed);
        return failed == 0 ? 0 : 1;
    }

    private static PlatformKey keyOf(AbstractLaplaceAdapter adapter, String mc, String loader) {
        return new PlatformKey(adapter.family(), mc, Support.blankToNull(loader));
    }
}
