package io.laplace.sprite.mixin;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 冲突图谱（15.11 P1）：多 mod 注入候选与高风险排序。 */
class MixinTargetGraphTest {

    private static final String ENTITY = "net.minecraft.world.entity.Entity";

    @Test
    void twoModsInjectingSameClassAreFlagged() {
        MixinTargetGraph graph = MixinTargetGraph.from(List.of(
                declaration("questmod", ENTITY, MixinInjectionKind.INJECT, "tick"),
                declaration("qolmod", ENTITY, MixinInjectionKind.INJECT, "tick")));

        List<MixinConflict> conflicts = graph.conflicts();

        assertEquals(1, conflicts.size());
        assertEquals(ENTITY, conflicts.get(0).targetClass());
        assertEquals(List.of("questmod", "qolmod"), conflicts.get(0).participants());
        assertEquals(MixinRisk.INJECT_OVERLAP, conflicts.get(0).risk());
        assertEquals(List.of(MixinInjectionKind.INJECT), conflicts.get(0).kinds());
        assertTrue(graph.conflictFor(ENTITY).isPresent());
    }

    @Test
    void singleModNeverBecomesConflictCandidate() {
        MixinTargetGraph graph = MixinTargetGraph.from(List.of(
                declaration("questmod", ENTITY, MixinInjectionKind.INJECT, "tick"),
                declaration("questmod", ENTITY, MixinInjectionKind.INJECT, "update")));

        assertTrue(graph.conflicts().isEmpty());
        assertTrue(graph.highRisk().isEmpty());
        assertTrue(graph.conflictFor(ENTITY).isEmpty());
    }

    @Test
    void overwriteOutranksDuplicateRedirect() {
        String level = "net.minecraft.world.Level";
        String gui = "net.minecraft.client.gui.Gui";
        MixinTargetGraph graph = MixinTargetGraph.from(List.of(
                declaration("questmod", level, MixinInjectionKind.OVERWRITE, "tick"),
                declaration("qolmod", level, MixinInjectionKind.INJECT, "tick"),
                declaration("questmod", gui, MixinInjectionKind.REDIRECT, "render"),
                declaration("qolmod", gui, MixinInjectionKind.REDIRECT, "render")));

        List<MixinConflict> risks = graph.highRisk();

        assertEquals(2, risks.size());
        assertEquals(MixinRisk.OVERWRITE, risks.get(0).risk());
        assertEquals(level, risks.get(0).targetClass());
        assertEquals(MixinRisk.DUPLICATE_REDIRECT, risks.get(1).risk());
        assertEquals(gui, risks.get(1).targetClass());
        assertTrue(MixinRisk.OVERWRITE.severity() > MixinRisk.DUPLICATE_REDIRECT.severity());
        assertTrue(MixinRisk.DUPLICATE_REDIRECT.severity() > MixinRisk.INJECT_OVERLAP.severity());
    }

    @Test
    void singleModDuplicateRedirectIsStillHighRisk() {
        MixinTargetGraph graph = MixinTargetGraph.from(List.of(
                declaration("questmod", ENTITY, MixinInjectionKind.REDIRECT, "tick"),
                declaration("questmod", ENTITY, MixinInjectionKind.REDIRECT, "tick")));

        assertEquals(1, graph.highRisk().size());
        assertEquals(MixinRisk.DUPLICATE_REDIRECT, graph.highRisk().get(0).risk());
    }

    private static InjectionDeclaration declaration(String modId, String target, MixinInjectionKind kind,
                                                    String method) {
        return new InjectionDeclaration(modId, target, kind, 1, MixinRequireMode.REQUIRE, method, "");
    }
}
