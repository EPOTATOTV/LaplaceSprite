package io.laplace.sprite.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * 拉普拉斯精灵 CLI 入口（第八章 cli 模块）。
 *
 * <p>子命令：{@code run} 跑基准、{@code gate} 判门禁、{@code replay} 回放轨迹、
 * {@code archive} 查证据与回归库、{@code test} 跑适配器矩阵冒烟、{@code console} 导出控制台数据；
 * 第十五章工具链轨：{@code debug}（调试轨 + 热重载双轨制）、{@code sandbox}（沙箱轨）、
 * {@code profile}（剖析轨）、{@code crash}（崩溃诊断器）、{@code protocol}（协议兼容轨）、
 * {@code clienttest}（CI 客户端测试轨）、{@code datapack}（数据包冒烟轨）、
 * {@code loadtest}（假玩家压测轨）、{@code mixin}（Mixin 生态适配轨）、
 * {@code render}（客户端渲染级测试轨）。</p>
 */
@Command(
        name = "laplace",
        mixinStandardHelpOptions = true,
        version = "Laplace Sprite 1.3.0",
        description = "面向全体 Minecraft 开发者的通用测试平台",
        subcommands = {
                RunCommand.class,
                GateCommand.class,
                ReplayCommand.class,
                ArchiveCommand.class,
                TestCommand.class,
                ConsoleCommand.class,
                DebugCommand.class,
                SandboxCommand.class,
                ProfileCommand.class,
                CrashCommand.class,
                ProtocolCommand.class,
                ClientTestCommand.class,
                DatapackCommand.class,
                LoadtestCommand.class,
                MixinCommand.class,
                RenderCommand.class
        })
public final class LaplaceCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new LaplaceCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }
}
