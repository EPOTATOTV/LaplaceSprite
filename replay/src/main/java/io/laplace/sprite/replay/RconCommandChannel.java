package io.laplace.sprite.replay;

import io.laplace.sprite.profile.CommandChannel;
import io.laplace.sprite.sandbox.RconConsole;

import java.util.Objects;

/**
 * 把沙箱轨的 {@link RconConsole} 适配成压测轨使用的 {@link CommandChannel}。
 *
 * <p>认证在首次下发时完成；之后每条命令直接走 RCON。适配器放在 replay 模块，
 * 因为只有它同时依赖 sandbox 与 loadtest，loadtest 本身不引入沙箱依赖。</p>
 */
public final class RconCommandChannel implements CommandChannel {

    private final RconConsole console;

    public RconCommandChannel(RconConsole console) {
        this.console = Objects.requireNonNull(console, "console");
    }

    @Override
    public String send(String command) {
        if (!console.authenticated()) {
            console.login();
        }
        return console.commandText(command);
    }
}
