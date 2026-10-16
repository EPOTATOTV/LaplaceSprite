package io.laplace.sprite.network;

import java.util.List;

/**
 * 客户端版本差异画像（4.3 / 14.7）：平台断言需要按版本区分命中体积、视高、鞘翅与流体推送行为。
 *
 * @param version                   版本标识（如 1.21、bedrock-1.21）
 * @param bedrock                   是否基岩版
 * @param hitboxWidth               命中体积宽度（格）
 * @param eyeHeight                 站立视高（格）
 * @param crouchEyeHeight           潜行视高（格）
 * @param hasElytra                 是否有鞘翅
 * @param hasAttackCooldown         是否有攻击冷却
 * @param fluidPushAffectsMovement  流体是否推动玩家位移
 */
public record ClientVersionProfile(
        String version,
        boolean bedrock,
        double hitboxWidth,
        double eyeHeight,
        double crouchEyeHeight,
        boolean hasElytra,
        boolean hasAttackCooldown,
        boolean fluidPushAffectsMovement) {

    private static final List<ClientVersionProfile> CATALOG = List.of(
            new ClientVersionProfile("1.8", false, 0.6, 1.62, 1.54, false, false, true),
            new ClientVersionProfile("1.12", false, 0.6, 1.62, 1.54, true, true, true),
            new ClientVersionProfile("1.16", false, 0.6, 1.62, 1.27, true, true, true),
            new ClientVersionProfile("1.20", false, 0.6, 1.62, 1.27, true, true, true),
            new ClientVersionProfile("1.21", false, 0.6, 1.62, 1.27, true, true, true),
            new ClientVersionProfile("bedrock-1.21", true, 0.6, 1.62, 1.27, true, true, false));

    public static List<ClientVersionProfile> catalog() {
        return CATALOG;
    }

    /** 未知版本按基岩前缀判断，其余回落到 1.21。 */
    public static ClientVersionProfile of(String clientVersion) {
        if (clientVersion != null) {
            for (ClientVersionProfile profile : CATALOG) {
                if (profile.version().equals(clientVersion)) {
                    return profile;
                }
            }
            if (clientVersion.startsWith("bedrock")) {
                return CATALOG.get(CATALOG.size() - 1);
            }
        }
        return CATALOG.get(CATALOG.size() - 2);
    }
}
