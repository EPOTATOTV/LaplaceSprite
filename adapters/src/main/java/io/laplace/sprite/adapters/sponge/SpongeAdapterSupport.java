package io.laplace.sprite.adapters.sponge;

import io.laplace.sprite.core.spi.SupportRange;

import java.util.ArrayList;
import java.util.List;

/**
 * Sponge 三实现的公共平台事实（14.3 Sponge 系）：装配目录、描述符、调度模型与版本矩阵口径一致，
 * 只有平台名与「SpongeNeo 双事件总线 / SpongeForge 识别顺序」这类差异留给各适配器补。
 */
final class SpongeAdapterSupport {

    /**
     * 版本矩阵：兼容线覆盖 LTS API 12 / MC 1.20.x 到主线 API 17 / MC 1.21.10，MC 26.x 预留观望。
     * SupportRange 只能表达连续区间，故 MC 取下界 1.20.0（上界放开），Sponge API 取 12～17。
     */
    static final SupportRange RANGE = new SupportRange("1.20.0", null, "12.0.0", "17.99.99");

    static final String ASSEMBLY = "mods/：三实现下插件都与模组混放，没有独立 plugins/";
    static final String DESCRIPTOR = SpongeJarClassifier.SPONGE_DESCRIPTOR;
    static final String SCHEDULER = "@Listener 事件总线（Order FIRST→LAST）+ Brigadier 命令树；主线程 tick";

    private SpongeAdapterSupport() {
    }

    /** 公共要点 + 实现特有要点。 */
    static List<String> notes(List<String> extra) {
        List<String> notes = new ArrayList<>(List.of(
                "JAR 内含 META-INF/sponge_plugins.json（API 8 起 @Plugin 注解元数据已移除）",
                "插件无 side 概念，服务端与客户端插件形态一致",
                "事件钩子用自有 @Listener + Order，不可复用 Bukkit 或 Forge 的扫描逻辑",
                "官方无 mock 服务端、无 GameTest，测试只能走真实服务端集成测试",
                "版本矩阵：主线 API 17 / MC 1.21.10；LTS 兼容线 API 12 / 1.20.x；MC 26.x 预留观望"));
        notes.addAll(extra);
        return List.copyOf(notes);
    }
}
