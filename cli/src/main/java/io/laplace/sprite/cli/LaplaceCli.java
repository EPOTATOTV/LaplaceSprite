package io.laplace.sprite.cli;

import io.laplace.sprite.core.i18n.I18n;
import io.laplace.sprite.core.i18n.Language;
import io.laplace.sprite.core.plugin.PluginRuntime;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.Optional;

/**
 * 拉普拉斯精灵 CLI 入口（第八章 cli 模块）。
 *
 * <p>子命令：{@code run} 跑基准、{@code gate} 判门禁、{@code replay} 回放轨迹、
 * {@code archive} 查证据与回归库、{@code test} 跑适配器矩阵冒烟、{@code console} 导出控制台数据；
 * 第十五章工具链轨：{@code debug}（调试轨 + 热重载双轨制）、{@code sandbox}（沙箱轨）、
 * {@code profile}（剖析轨）、{@code crash}（崩溃诊断器）、{@code protocol}（协议兼容轨）、
 * {@code clienttest}（CI 客户端测试轨）、{@code datapack}（数据包冒烟轨）、
 * {@code loadtest}（假玩家压测轨）、{@code mixin}（Mixin 生态适配轨）、
 * {@code render}（客户端渲染级测试轨）、{@code market}（样本市场）、
 * {@code pacc}（PACC 夜间基准）、{@code gametest}（四加载器 GameTest）、
 * {@code plugin}（业务插件列表）、{@code game}（全版本游戏内核管理）、
 * {@code i18n}（界面语言与文案）、{@code audit}（全局证据审计）。</p>
 */
@Command(
        name = "laplace",
        mixinStandardHelpOptions = true,
        version = "Laplace Sprite 1.5.0",
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
                RenderCommand.class,
                MarketCommand.class,
                PaccCommand.class,
                GameTestCommand.class,
                PluginCommand.class,
                GameCommand.class,
                I18nCommand.class,
                AuditCommand.class
        })
public final class LaplaceCli implements Runnable {

    /** 只是为了让 picocli 生成全局帮助；main 在 execute 前扫描同一参数并先落到 {@link I18n}。 */
    @Option(names = "--lang", scope = CommandLine.ScopeType.INHERIT,
            paramLabel = "<zh-CN|en-US>", description = "界面语言：zh-CN 或 en-US")
    String lang;

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        PluginRuntime.install(PluginRuntime.discover());
        String requested = langOption(args);
        if (requested != null) {
            Optional<Language> parsed = Language.parse(requested);
            if (parsed.isEmpty()) {
                System.out.println(I18n.get().t("cli.lang.invalid", requested));
                System.exit(2);
            }
            I18n.get().setLanguage(parsed.get());
        }
        int exitCode = new CommandLine(new LaplaceCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }

    /** 手工扫描而不是 parseArgs：语言必须在任何子命令输出前生效，且非法值要返回 2 而不是走用法报错。 */
    private static String langOption(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--lang".equals(arg)) {
                return i + 1 < args.length ? args[i + 1] : "";
            }
            if (arg.startsWith("--lang=")) {
                return arg.substring("--lang=".length());
            }
        }
        return null;
    }
}
