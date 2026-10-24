package io.laplace.sprite.cli;

import io.laplace.sprite.core.plugin.PluginDescriptor;
import io.laplace.sprite.core.plugin.PluginRegistry;
import io.laplace.sprite.core.plugin.PluginRuntime;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace plugin}：查看业务插件的注册与激活状态（v3.0 L3-01）。
 */
@Command(name = "plugin", description = "业务插件列表与激活状态",
        mixinStandardHelpOptions = true,
        subcommands = {PluginCommand.ListCommand.class})
final class PluginCommand implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "list", description = "列出发现的插件：id / 版本 / 能力 / 是否激活",
            mixinStandardHelpOptions = true)
    static final class ListCommand implements Callable<Integer> {

        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            PluginRegistry registry = PluginRuntime.registry();
            List<PluginDescriptor> descriptors = registry.descriptors();
            var writer = spec.commandLine().getOut();
            if (descriptors.isEmpty()) {
                writer.println("未发现业务插件");
                return 0;
            }
            writer.printf("业务插件（%d 个）%n", descriptors.size());
            for (PluginDescriptor descriptor : descriptors) {
                String capabilities = descriptor.capabilities().isEmpty()
                        ? "-" : String.join(",", descriptor.capabilities());
                writer.printf("  %s · %s · 能力 %s · %s%n", descriptor.id(), descriptor.version(),
                        capabilities, registry.isActive(descriptor.id()) ? "已激活" : "未激活");
            }
            return 0;
        }
    }
}
