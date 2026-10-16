package io.laplace.sprite.adapters.hybrid;

import java.util.ArrayList;
import java.util.List;

/**
 * 混合端已知坑（14.3 混合端组合适配器要点）。
 *
 * <p>这些是组合适配层要老实摆出来的问题，不是待解决的 TODO：官方把混合端当二等公民，
 * 矩阵必须用「模组 × 插件 × MC 版本」穷举把它们覆盖住，适配层只负责识别与记录。</p>
 */
public final class HybridPitfalls {

    private HybridPitfalls() {
    }

    public static List<String> known() {
        return List.of(
                "事件双发冲突：模组事件与 Bukkit Listener 对同一动作各发一次，须按玩家/tick/类型/载荷折叠后进流",
                "类加载顺序与类阴影：Bukkit 类与模组类可能重复或互相遮蔽，断言基线须固定混合端自身的加载顺序",
                "命令树不兼容：Connector 与 Brigadier 命令树并存，注册冲突与覆盖顺序必须进矩阵用例",
                "插件原版语义假设落空：插件按原版/CraftBukkit 语义写下的假设在混合端未必成立");
    }

    /** 已知坑 + 平台特有说明。 */
    public static List<String> notes(List<String> extra) {
        List<String> notes = new ArrayList<>(known());
        notes.addAll(extra);
        return List.copyOf(notes);
    }
}
