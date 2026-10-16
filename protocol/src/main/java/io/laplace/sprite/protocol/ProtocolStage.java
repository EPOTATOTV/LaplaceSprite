package io.laplace.sprite.protocol;

import java.util.List;

/**
 * 最小闭环阶段（15.6）：登录 → 进世界 → 放置方块 → 打怪 → 收发聊天。
 *
 * <p>声明顺序即执行顺序，前一阶段通过才进下一阶段。</p>
 */
public enum ProtocolStage {

    LOGIN,
    JOIN_WORLD,
    PLACE_BLOCK,
    ATTACK_ENTITY,
    CHAT;

    /** 闭环顺序。 */
    public static List<ProtocolStage> ordered() {
        return List.of(values());
    }
}
