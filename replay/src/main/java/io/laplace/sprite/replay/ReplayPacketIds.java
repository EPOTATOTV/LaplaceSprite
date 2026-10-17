package io.laplace.sprite.replay;

import java.util.Optional;

/**
 * 抽取脚本所需的协议包 id。
 *
 * <p>目前只登记协议 763（Minecraft 1.20.1）的 serverbound 玩阶段包 id；其它版本不猜，
 * 调用方需要显式传入对应版本的映射。协议号来自 {@code metaData.json} 的 {@code protocol}。</p>
 *
 * @param position     玩家位置包
 * @param positionLook 玩家位置与视角包
 * @param look         玩家视角包
 * @param onGround     玩家着地包
 * @param chatCommand  聊天指令包
 * @param chatMessage  聊天消息包
 */
public record ReplayPacketIds(int position, int positionLook, int look, int onGround,
                              int chatCommand, int chatMessage) {

    public ReplayPacketIds {
        if (position < 0 || positionLook < 0 || look < 0 || onGround < 0 || chatCommand < 0
                || chatMessage < 0) {
            throw new IllegalArgumentException("包 id 不能为负数");
        }
    }

    /** Minecraft 1.20.1 / 协议 763。 */
    public static ReplayPacketIds mc1201() {
        return new ReplayPacketIds(0x14, 0x15, 0x16, 0x17, 0x04, 0x05);
    }

    public static Optional<ReplayPacketIds> forProtocol(int protocol) {
        return protocol == 763 ? Optional.of(mc1201()) : Optional.empty();
    }
}
