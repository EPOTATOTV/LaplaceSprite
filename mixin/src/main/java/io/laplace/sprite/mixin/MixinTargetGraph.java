package io.laplace.sprite.mixin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * target → mod 注入有向图（15.11 P1）。
 *
 * <p>静态织入面预检：按目标类聚合所有 mod 的注入声明。被两个及以上 mod 注入的类列为冲突候选；
 * {@code @Overwrite} 与同一调用/字段上的重复 {@code @Redirect} 单独升为高风险。</p>
 */
public final class MixinTargetGraph {

    /**
     * 一条参与注入的记录。
     *
     * @param modId  模组 id
     * @param kind   注入方式
     * @param method 目标方法或字段，未知为空
     */
    public record Participation(String modId, MixinInjectionKind kind, String method) {

        public Participation {
            Objects.requireNonNull(modId, "modId");
            Objects.requireNonNull(kind, "kind");
            method = method == null || method.isBlank() ? null : method;
        }
    }

    private final Map<String, List<Participation>> byTarget;

    private MixinTargetGraph(Map<String, List<Participation>> byTarget) {
        this.byTarget = byTarget;
    }

    /** 从声明列表构图；目标类与声明顺序保持输入顺序，便于报告稳定 diff。 */
    public static MixinTargetGraph from(List<InjectionDeclaration> declarations) {
        Objects.requireNonNull(declarations, "declarations");
        Map<String, List<Participation>> byTarget = new LinkedHashMap<>();
        for (InjectionDeclaration declaration : declarations) {
            byTarget.computeIfAbsent(declaration.targetClass(), key -> new ArrayList<>())
                    .add(new Participation(declaration.modId(), declaration.kind(), declaration.method()));
        }
        Map<String, List<Participation>> frozen = new LinkedHashMap<>();
        byTarget.forEach((target, participants) -> frozen.put(target, List.copyOf(participants)));
        return new MixinTargetGraph(frozen);
    }

    /** 图的目标类 → 参与注入记录。 */
    public Map<String, List<Participation>> targetToParticipants() {
        return byTarget;
    }

    public Set<String> targets() {
        return byTarget.keySet();
    }

    /** 冲突候选：目标类被两个及以上不同 mod 注入。 */
    public List<MixinConflict> conflicts() {
        List<MixinConflict> conflicts = new ArrayList<>();
        byTarget.forEach((target, participants) -> {
            if (distinctMods(participants).size() >= 2) {
                conflicts.add(conflict(target, participants, baseRisk(participants)));
            }
        });
        conflicts.sort(conflictOrder());
        return List.copyOf(conflicts);
    }

    /**
     * 高风险注入：只要出现 {@code @Overwrite} 或同一调用/字段的重复 {@code @Redirect} 就入列，
     * 单 mod 也会入列——这类写法不需要「两个 mod」才算问题。
     */
    public List<MixinConflict> highRisk() {
        List<MixinConflict> risks = new ArrayList<>();
        byTarget.forEach((target, participants) -> {
            MixinRisk risk = baseRisk(participants);
            if (risk != MixinRisk.INJECT_OVERLAP) {
                risks.add(conflict(target, participants, risk));
            }
        });
        risks.sort(conflictOrder());
        return List.copyOf(risks);
    }

    /** 单目标查询，崩溃诊断做类反查时用。 */
    public Optional<MixinConflict> conflictFor(String targetClass) {
        List<Participation> participants = byTarget.get(targetClass);
        if (participants == null || distinctMods(participants).size() < 2) {
            return Optional.empty();
        }
        return Optional.of(conflict(targetClass, participants, baseRisk(participants)));
    }

    private static Comparator<MixinConflict> conflictOrder() {
        return Comparator.comparingInt((MixinConflict conflict) -> conflict.risk().severity()).reversed()
                .thenComparing(MixinConflict::targetClass);
    }

    private static MixinRisk baseRisk(List<Participation> participants) {
        if (participants.stream().anyMatch(entry -> entry.kind() == MixinInjectionKind.OVERWRITE)) {
            return MixinRisk.OVERWRITE;
        }
        if (hasDuplicateRedirect(participants)) {
            return MixinRisk.DUPLICATE_REDIRECT;
        }
        return MixinRisk.INJECT_OVERLAP;
    }

    /** 同一 (目标类, 方法/字段) 上出现多次 @Redirect。 */
    private static boolean hasDuplicateRedirect(List<Participation> participants) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Participation entry : participants) {
            if (entry.kind() == MixinInjectionKind.REDIRECT) {
                counts.merge(entry.method() == null ? "*" : entry.method(), 1, Integer::sum);
            }
        }
        return counts.values().stream().anyMatch(count -> count > 1);
    }

    private static List<String> distinctMods(List<Participation> participants) {
        Set<String> mods = new LinkedHashSet<>();
        participants.forEach(entry -> mods.add(entry.modId()));
        return List.copyOf(mods);
    }

    private static MixinConflict conflict(String target, List<Participation> participants, MixinRisk risk) {
        List<String> mods = distinctMods(participants);
        List<MixinInjectionKind> kinds = new ArrayList<>(new LinkedHashSet<>(
                participants.stream().map(Participation::kind).toList()));
        String reason = switch (risk) {
            case OVERWRITE -> "%s 上存在 @Overwrite，整体替换方法字节码；参与 mod：%s"
                    .formatted(target, String.join(", ", mods));
            case DUPLICATE_REDIRECT -> "%s 的同一调用/字段被重复 @Redirect（官方只允许一次）；参与 mod：%s"
                    .formatted(target, String.join(", ", mods));
            case INJECT_OVERLAP -> "%s 被 %d 个 mod 同时注入；参与 mod：%s"
                    .formatted(target, mods.size(), String.join(", ", mods));
        };
        return new MixinConflict(target, mods, kinds, risk, reason);
    }
}
