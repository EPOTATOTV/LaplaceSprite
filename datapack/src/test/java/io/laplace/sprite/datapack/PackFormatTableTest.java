package io.laplace.sprite.datapack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** pack_format 对照表（15.8）：至少覆盖三个版本与一组不兼容。 */
class PackFormatTableTest {

    private final PackFormatTable table = new PackFormatTable();

    @Test
    void mapsKnownVersionsToVanillaFormats() {
        assertEquals(4, table.formatFor("1.13").orElseThrow());
        assertEquals(15, table.formatFor("1.20.1").orElseThrow());
        assertEquals(61, table.formatFor("1.21.4").orElseThrow());
        assertEquals(71, table.formatFor("1.21.5").orElseThrow());
        assertEquals(81, table.formatFor("1.21.8").orElseThrow());
        assertEquals(85, table.formatFor("26.1").orElseThrow());
        assertEquals(86, table.formatFor("26.2").orElseThrow());
    }

    @Test
    void versionsOutsideTheTableAreUnknown() {
        assertTrue(table.formatFor("1.12.2").isEmpty());
        assertTrue(table.formatFor("1.30").isEmpty());
    }

    @Test
    void detectsIncompatiblePair() {
        PackMeta oldPack = new PackMeta(61, "1.21.4 pack", List.of(), List.of(), Map.of());

        assertTrue(table.compatible("1.21.4", oldPack));
        assertFalse(table.compatible("1.21.5", oldPack), "61 不能加载到期望 71 的 1.21.5");
    }

    @Test
    void supportedFormatsWidensCompatibility() {
        PackMeta flexible = new PackMeta(61, "61..71", List.of(new PackFormatRange(61, 71)),
                List.of(), Map.of());

        assertTrue(table.compatible("1.21.4", flexible));
        assertTrue(table.compatible("1.21.5", flexible));
        assertFalse(table.compatible("1.21.6", flexible));
    }

    @Test
    void unknownVersionIsNeverCompatible() {
        PackMeta any = new PackMeta(71, "x", List.of(), List.of(), Map.of());

        assertFalse(table.compatible("1.30", any), "没有可信期望值时不给通过");
    }
}
