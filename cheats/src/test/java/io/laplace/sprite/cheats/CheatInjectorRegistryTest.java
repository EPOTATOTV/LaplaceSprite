package io.laplace.sprite.cheats;

import io.laplace.sprite.core.model.CheatType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 注入器注册表覆盖全部作弊类型。 */
class CheatInjectorRegistryTest {

    private final CheatInjectorRegistry registry = new CheatInjectorRegistry();

    @Test
    void everyCheatTypeResolvesToInjector() {
        for (CheatType type : CheatType.values()) {
            assertNotNull(registry.of(type), type.name());
            assertEquals(type, registry.of(type).type());
        }
        assertEquals(EnumSet.allOf(CheatType.class), registry.supported());
    }

    @Test
    void unknownTypeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> registry.of(null));
    }
}
