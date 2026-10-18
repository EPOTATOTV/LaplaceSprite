package io.laplace.sprite.hotreload;

import java.util.List;

/**
 * 重载能力自检矩阵（15.2）：输出（JVM 类型 × 改动类型）判定表，可查询也可渲染成 Markdown。
 */
public final class HotReloadMatrix {

    private final HotReloadCapability capability;

    public HotReloadMatrix() {
        this(new HotReloadCapability());
    }

    public HotReloadMatrix(HotReloadCapability capability) {
        this.capability = capability == null ? new HotReloadCapability() : capability;
    }

    public HotReloadCapability.Decision at(JvmKind jvm, ChangeKind kind) {
        return capability.decide(jvm, kind);
    }

    public HotReloadCapability.Decision at(JvmProbe probe, ChangeKind kind) {
        return capability.decide(probe.kind(), kind);
    }

    public List<JvmKind> jvms() {
        return List.of(JvmKind.values());
    }

    public List<ChangeKind> changes() {
        return List.of(ChangeKind.values());
    }

    public String toMarkdown() {
        return render(null);
    }

    /** 单个 JVM 的能力自检表。 */
    public String toMarkdown(JvmKind jvm) {
        return render(jvm);
    }

    private String render(JvmKind only) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("| JVM \\ 改动类型 |");
        for (ChangeKind kind : changes()) {
            markdown.append(' ').append(kind.displayName()).append(" |");
        }
        markdown.append('\n').append("| --- |");
        for (int i = 0; i < changes().size(); i++) {
            markdown.append(" --- |");
        }
        markdown.append('\n');
        for (JvmKind jvm : jvms()) {
            if (only != null && jvm != only) {
                continue;
            }
            markdown.append("| ").append(jvm.displayName()).append(" |");
            for (ChangeKind kind : changes()) {
                markdown.append(' ')
                        .append(at(jvm, kind).hotSwappable() ? "可热生效" : HotReloadCapability.RESTART_REQUIRED)
                        .append(" |");
            }
            markdown.append('\n');
        }
        markdown.append('\n').append("Mixin 热交换需 ").append(HotReloadCapability.SPONGE_MIXIN_AGENT)
                .append("；mods/plugins 目录变更一律走冷启动轨。").append('\n');
        return markdown.toString();
    }
}
