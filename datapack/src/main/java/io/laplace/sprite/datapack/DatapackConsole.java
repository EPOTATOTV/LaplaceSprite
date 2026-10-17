package io.laplace.sprite.datapack;

/**
 * 控制台缝（15.8）：真实实现走 {@link RconDatapackConsole}，离线用内存替身。
 *
 * <p>命令按文档原样下发（带前导斜杠），RCON 实现在传输层去掉斜杠。</p>
 */
@FunctionalInterface
public interface DatapackConsole {

    /** 下发一条命令，返回控制台回显；没有回显时返回空串。 */
    String execute(String command);
}
