package io.laplace.sprite.profile;

import java.util.List;

/**
 * 一条 spark 命令（15.3）：子命令 + 参数。
 *
 * @param subcommand 子命令，如 tps / health / profiler
 * @param args       子命令后的参数
 */
public record SparkCommand(String subcommand, List<String> args) {

    public SparkCommand {
        args = args == null ? List.of() : List.copyOf(args);
    }

    /** 完整命令行，交给命令通道下发。 */
    public String commandLine() {
        if (args.isEmpty()) {
            return "/spark " + subcommand;
        }
        return "/spark " + subcommand + " " + String.join(" ", args);
    }

    @Override
    public String toString() {
        return commandLine();
    }
}
