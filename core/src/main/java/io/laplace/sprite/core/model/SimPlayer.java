package io.laplace.sprite.core.model;

/**
 * 模拟玩家（11.4 SimPlayerRegistry 条目）。
 *
 * @param name    玩家名
 * @param simId   玩家测试实体 id
 * @param type    类型（合法 / 作弊 / 管理员）
 * @param behavior 行为模型
 * @param cheatSpecId 作弊样本 id；合法玩家为空
 */
public record SimPlayer(String name, String simId, PlayerType type, BehaviorProfile behavior,
                        String cheatSpecId) {

    public static SimPlayer legit(String name, String simId, BehaviorProfile behavior) {
        return new SimPlayer(name, simId, PlayerType.LEGIT, behavior, null);
    }

    public static SimPlayer cheat(String name, String simId, String cheatSpecId) {
        return new SimPlayer(name, simId, PlayerType.CHEAT, null, cheatSpecId);
    }

    public static SimPlayer admin(String name, String simId) {
        return new SimPlayer(name, simId, PlayerType.ADMIN, null, null);
    }
}
