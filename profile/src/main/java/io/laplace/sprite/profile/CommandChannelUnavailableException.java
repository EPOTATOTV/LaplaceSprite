package io.laplace.sprite.profile;

/** 命令通道不可用：离线降级的触发信号。 */
public class CommandChannelUnavailableException extends RuntimeException {

    public CommandChannelUnavailableException(String message) {
        super(message);
    }
}
