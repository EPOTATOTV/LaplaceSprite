package io.laplace.sprite.datapack;

/**
 * 一次数据包冒烟用例里下发过的一步。
 *
 * @param command 下发的命令
 * @param reply   控制台回显；执行异常时为空串
 */
public record DatapackStep(String command, String reply) {

    public DatapackStep {
        command = command == null ? "" : command;
        reply = reply == null ? "" : reply;
    }
}
