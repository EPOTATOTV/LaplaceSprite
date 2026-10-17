package io.laplace.sprite.datapack;

import io.laplace.sprite.sandbox.RconConsole;

import java.util.Objects;

/**
 * 走沙箱 RCON 控制面的实现（15.8）：复用沙箱轨的端口与密码，不再另起一套连接。
 */
public final class RconDatapackConsole implements DatapackConsole {

    private final RconConsole console;

    public RconDatapackConsole(RconConsole console) {
        this.console = Objects.requireNonNull(console, "console");
    }

    @Override
    public String execute(String command) {
        // RconConsole 自己会去掉前导斜杠
        return console.commandText(command);
    }
}
