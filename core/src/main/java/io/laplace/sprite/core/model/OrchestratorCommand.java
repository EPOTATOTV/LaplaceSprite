package io.laplace.sprite.core.model;

import java.util.List;

/**
 * /ls 命令（11.4 /ls 命令面板）：inspect / teleport / scenario / result 子命令与回执。
 */
public record OrchestratorCommand(String name, List<String> args, String receipt) {

    public OrchestratorCommand {
        args = args == null ? List.of() : List.copyOf(args);
    }

    public static OrchestratorCommand of(String name, String... args) {
        return new OrchestratorCommand(name, List.of(args), "");
    }

    public OrchestratorCommand withReceipt(String newReceipt) {
        return new OrchestratorCommand(name, args, newReceipt);
    }
}
