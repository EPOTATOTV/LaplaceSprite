package io.laplace.sprite.cheats;

import io.laplace.sprite.core.model.SampleTierName;
import io.laplace.sprite.core.model.CheatType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 作弊矩阵的聚合口径。 */
class AnomalyMatrixTest {

    private final AnomalyMatrix matrix = new AnomalyMatrix();

    @Test
    void totalConfigurationsEqualsSumOfDeclaredTiers() {
        int expected = matrix.specs().stream().mapToInt(spec -> spec.tiers().size()).sum();
        assertEquals(expected, matrix.totalConfigurations());
        assertEquals(matrix.specs().size() * SampleTierName.values().length, matrix.totalConfigurations());
        assertTrue(matrix.totalConfigurations() >= 120, "配置总数不足: " + matrix.totalConfigurations());
    }

    @Test
    void everyTypeIsPopulatedAndAllTiersAreListed() {
        assertEquals(EnumSet.allOf(CheatType.class), matrix.byType().keySet());
        assertTrue(matrix.byType().values().stream().noneMatch(List::isEmpty));
        assertEquals(List.of(SampleTierName.MIN, SampleTierName.DEFAULT, SampleTierName.MAX, SampleTierName.HIDDEN),
                matrix.tiers());
    }

    @Test
    void categoriesCoverWholeMatrix() {
        assertEquals(EnumSet.allOf(SampleCategory.class), matrix.byCategory().keySet());
        assertTrue(matrix.byCategory().values().stream().noneMatch(List::isEmpty));
        assertEquals(matrix.samples().size(), matrix.byCategory().values().stream()
                .mapToInt(List::size).sum());

        assertTrue(matrix.samples().size() >= 60, "样本库规模不足: " + matrix.samples().size());

        long nonCheat = matrix.samples().stream().filter(spec -> !spec.isCheat()).count();
        assertTrue(nonCheat >= 12, "通用异常样本不足: " + nonCheat);
        assertEquals(matrix.specs().size(), matrix.samples().stream()
                .filter(SampleSpec::isCheat).count());
    }
}
