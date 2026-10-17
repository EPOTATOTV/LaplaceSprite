package io.laplace.sprite.core.model;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 13.6 全类型模组测试方法矩阵 / 9 验收 11。 */
class TargetTypeMatrixTest {

    private final TargetTypeMatrix matrix = new TargetTypeMatrix();

    @Test
    void coversAllTwelveTargetTypes() {
        List<TargetTypeProfile> profiles = matrix.profiles();

        assertEquals(12, matrix.coveredCount());
        assertEquals(12, profiles.size());
        assertEquals(EnumSet.allOf(TargetType.class),
                profiles.stream().map(TargetTypeProfile::type).collect(Collectors.toSet()));
    }

    @Test
    void everyProfileHasTestFocusToolsRisksProjectsAndSources() {
        for (TargetTypeProfile profile : matrix.profiles()) {
            String name = profile.type().displayName();
            assertTrue(!profile.testFocus().isEmpty(), name + " 缺测试重点");
            assertTrue(!profile.frameworksAndTools().isEmpty(), name + " 缺框架与工具");
            assertTrue(!profile.typicalRisks().isEmpty(), name + " 缺典型风险");
            assertTrue(!profile.representativeProjects().isEmpty(), name + " 缺代表项目");
            assertTrue(!profile.sources().isEmpty(), name + " 缺来源");
            assertTrue(profile.sources().stream().allMatch(url -> url.startsWith("https://")),
                    name + " 的来源必须是 URL");
        }
    }

    @Test
    void detectionEngineIsNotTheDefaultOrOnlyRow() {
        List<TargetTypeProfile> profiles = matrix.profiles();

        assertTrue(profiles.stream().noneMatch(
                        p -> p.type().displayName().contains("检测引擎")),
                "检测引擎只是众多被测目标之一，不能作为默认行");
        assertEquals(12, profiles.stream().map(TargetTypeProfile::type).distinct().count());
    }

    @Test
    void matrixCarriesRealRepresentativeProjects() {
        Set<String> projects = matrix.profiles().stream()
                .flatMap(profile -> profile.representativeProjects().stream())
                .collect(Collectors.toSet());
        long total = matrix.profiles().stream()
                .mapToLong(profile -> profile.representativeProjects().size())
                .sum();

        assertTrue(projects.contains("Cobblemon"));
        assertTrue(projects.contains("Create"));
        assertTrue(projects.contains("Sodium"));
        assertTrue(projects.contains("MockBukkit"));
        assertTrue(projects.contains("Architectury API"));
        assertTrue(projects.contains("PackTest"));
        assertTrue(projects.contains("Geyser"));
        assertTrue(projects.contains("TerraBlender"));
        assertTrue(total >= 30, "12 类合计代表项目数 = " + total);
    }
}
