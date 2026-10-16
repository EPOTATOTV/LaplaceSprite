package io.laplace.sprite.cheats;

import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.CheatType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 作弊矩阵的聚合口径。 */
class CheatMatrixTest {

    private final CheatMatrix matrix = new CheatMatrix();

    @Test
    void totalConfigurationsEqualsSumOfDeclaredTiers() {
        int expected = matrix.specs().stream().mapToInt(spec -> spec.tiers().size()).sum();
        assertEquals(expected, matrix.totalConfigurations());
        assertEquals(matrix.specs().size() * CheatTierName.values().length, matrix.totalConfigurations());
        assertTrue(matrix.totalConfigurations() >= 120, "配置总数不足: " + matrix.totalConfigurations());
    }

    @Test
    void everyTypeIsPopulatedAndAllTiersAreListed() {
        assertEquals(EnumSet.allOf(CheatType.class), matrix.byType().keySet());
        assertTrue(matrix.byType().values().stream().noneMatch(List::isEmpty));
        assertEquals(List.of(CheatTierName.MIN, CheatTierName.DEFAULT, CheatTierName.MAX, CheatTierName.HIDDEN),
                matrix.tiers());
    }
}
