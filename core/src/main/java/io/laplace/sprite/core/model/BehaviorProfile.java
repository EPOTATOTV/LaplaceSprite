package io.laplace.sprite.core.model;

/**
 * 合法玩家行为模型（4.1 行为模型库）。
 */
public enum BehaviorProfile {
    // 移动
    WALK, RUN, SPRINT, JUMP, SNEAK, SWIM, ELYTRA, BOAT, RIDE,
    GODBRIDGE, TELLY, NINJA, MLG, PEARL, KNOCKBACK_RESPONSE,
    // 战斗
    CLICKING, TARGET_SWITCH, MULTI_ENTITY,
    // 交互
    INVENTORY_SORT, PLACE, MINE
}
