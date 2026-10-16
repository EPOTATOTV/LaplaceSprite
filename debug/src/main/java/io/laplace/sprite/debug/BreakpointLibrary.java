package io.laplace.sprite.debug;

import java.util.List;
import java.util.Optional;

/**
 * 条件断点模板库（15.1）：预置 MC 高频场景的条件断点表达式。
 *
 * <p>表达式用 Yarn / Mojang 官方映射里的名字（1.21 系），换映射或换版本时按 {@code notes} 调整即可。</p>
 */
public final class BreakpointLibrary {

    private final List<BreakpointTemplate> templates;

    public BreakpointLibrary(List<BreakpointTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            throw new IllegalArgumentException("模板库不能为空");
        }
        this.templates = List.copyOf(templates);
    }

    public static BreakpointLibrary defaults() {
        return new BreakpointLibrary(List.of(
                new BreakpointTemplate("item-in-hand", "玩家手持指定物品且选中该槽", "player-interact",
                        "slotIndex == player.getInventory().selected && player.getMainHandItem().is(Items.DIAMOND_SWORD)",
                        "1.21 起为 getMainHandItem()/is(Item)；1.20 及以前用 getItemInHand()/getItem() == Items.X"),
                new BreakpointTemplate("block-tag", "目标方块 tag 命中 stones 系", "block-break",
                        "state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.STONE_BRICKS)",
                        "方块状态用 is(TagKey) 判断；旧版本是 isIn(TagKey)"),
                new BreakpointTemplate("entity-type", "指定类型实体 tick", "entity-tick",
                        "entity.getType() == EntityType.CREEPER",
                        "实体基类方法在客户端与服务端同名，双端断点可共用"),
                new BreakpointTemplate("tick-number", "指定 tick 且按秒对齐", "tick-loop",
                        "tick % 20 == 0 && tick > 600",
                        "tick 为局部变量名，不同注入点可能叫 ticks / worldTick，需按现场改名"),
                new BreakpointTemplate("chat-contains", "聊天内容包含关键字", "chat",
                        "message.getString().contains(\"laplace\")",
                        "1.19 起聊天为签名消息，取纯文本用 getString()"),
                new BreakpointTemplate("damage-event", "单次伤害超过阈值且存在攻击者", "damage",
                        "amount > 6.0f && source.getEntity() != null",
                        "伤害来源方法名在 1.20 后为 getEntity()；旧版 getAttacker()"),
                new BreakpointTemplate("chunk-load", "原点区块加载", "chunk-load",
                        "pos.x == 0 && pos.z == 0",
                        "pos 为 ChunkPos，服务端线程上判定，客户端断点注意线程过滤"),
                new BreakpointTemplate("inventory-slot", "热键栏第 1 格为空", "inventory",
                        "slot == 36 && stack.isEmpty()",
                        "容器槽位编号随容器类型变化，玩家背包热键栏从 36 起")
        ));
    }

    public List<BreakpointTemplate> all() {
        return templates;
    }

    public int size() {
        return templates.size();
    }

    public Optional<BreakpointTemplate> byId(String id) {
        return templates.stream().filter(template -> template.id().equals(id)).findFirst();
    }

    public List<BreakpointTemplate> byScene(String scene) {
        return templates.stream().filter(template -> template.scene().equals(scene)).toList();
    }
}
