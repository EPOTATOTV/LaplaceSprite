package io.laplace.sprite.profile;

/**
 * 命令通道（15.3）：沿用 RCON 那套「下发一行命令、收一段文本回执」的形态。
 *
 * <p>本模块不依赖沙箱轨，通道实现由使用方注入（RCON / 进程 stdin / 测试用内存通道）。</p>
 */
public interface CommandChannel {

    /**
     * 下发命令并返回文本回执。
     *
     * @throws CommandChannelUnavailableException 通道不可用时抛出，调用方据此走离线降级，
     *                                            不允许吞掉异常后伪造回执。
     */
    String send(String command);
}
