package io.laplace.sprite.loadtest;

import io.laplace.sprite.profile.CommandChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * 服务端内假人启动器（15.9）：把剧本渲染成 Carpet 的 {@code /player} 命令，
 * 通过 {@link CommandChannel} 下发。本模块不依赖沙箱模块，RCON 适配由调用方提供
 * （见 replay 模块的 RconCommandChannel）。
 *
 * <p>命令顺序固定为「全部 spawn → 逐个动作 → 全部 stop」，避免假人还没生成就开始动作。</p>
 *
 * <p>动作映射按 Carpet 原生子命令：走动用 {@code move forward}，放置用 {@code use}，
 * 挖掘与攻击在客户端同为左键，Carpet 没有独立 mine 子命令，因此都落到
 * {@code attack continuous}。聊天与待机不渲染：Carpet 假人没有原生聊天子命令，
 * 聊天属于网络层动作，由 Mineflayer 后端覆盖。</p>
 */
public final class CarpetLauncher {

    public static final String DEFAULT_NAME_PREFIX = "lp-bot-";

    private final CommandChannel channel;
    private final String namePrefix;

    public CarpetLauncher(CommandChannel channel) {
        this(channel, DEFAULT_NAME_PREFIX);
    }

    public CarpetLauncher(CommandChannel channel, String namePrefix) {
        this.channel = Objects.requireNonNull(channel, "channel");
        if (namePrefix == null || namePrefix.isBlank()) {
            throw new IllegalArgumentException("假人名前缀不能为空");
        }
        this.namePrefix = namePrefix;
    }

    /** 下发全部命令并返回实际下发的命令行。 */
    public List<String> execute(LoadScenario scenario) {
        List<String> commands = commands(scenario);
        for (String command : commands) {
            channel.send(command);
        }
        return commands;
    }

    /** spawn → 动作 → stop 的完整命令序列。 */
    public List<String> commands(LoadScenario scenario) {
        int count = scenario.maxConcurrency();
        List<String> commands = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            commands.add("/player " + name(index) + " spawn");
        }
        for (BotAction action : scenario.actions()) {
            Optional<String> subcommand = carpetAction(action);
            if (subcommand.isEmpty()) {
                continue;
            }
            for (int index = 1; index <= count; index++) {
                commands.add("/player " + name(index) + " " + subcommand.get());
            }
        }
        for (int index = 1; index <= count; index++) {
            commands.add("/player " + name(index) + " stop");
        }
        return List.copyOf(commands);
    }

    /** 生成 1..count 号假人的 spawn 命令，供需要分批爬坡的调用方复用。 */
    public List<String> spawnCommands(int count) {
        List<String> commands = new ArrayList<>(count);
        for (int index = 1; index <= count; index++) {
            commands.add("/player " + name(index) + " spawn");
        }
        return List.copyOf(commands);
    }

    /** 单个动作对应的 Carpet 子命令；不支持的动作为空。 */
    public static Optional<String> carpetAction(BotAction action) {
        return switch (action) {
            case JOIN, CHAT, IDLE -> Optional.empty();
            case WALK -> Optional.of("move forward");
            case MINE, ATTACK -> Optional.of("attack continuous");
            case PLACE -> Optional.of("use continuous");
        };
    }

    private String name(int index) {
        return normalizeName(namePrefix + index);
    }

    /** 假人名只允许小写字母、数字与连字符，避免 RCON 命令被空白截断。 */
    static String normalizeName(String raw) {
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
    }
}
