package io.laplace.sprite.cheats;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.TestSpec;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.SampleTierName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 声明式样本库的加载与查询。 */
class CheatCatalogTest {

    private static final Path SAMPLE_DIR = Paths.get("src", "main", "resources", "cheats", "samples");

    @Test
    void defaultsCoverAllTypesWithUniqueIds() {
        List<TestSpec> specs = new CheatCatalog().loadDefaults();
        assertTrue(specs.size() >= 30, "样本数不足: " + specs.size());
        Set<CheatType> types = specs.stream().map(TestSpec::type).collect(Collectors.toSet());
        assertEquals(EnumSet.allOf(CheatType.class), types);
        assertEquals(specs.size(), specs.stream().map(TestSpec::id).distinct().count());
        assertTrue(specs.stream().allMatch(spec -> "detection-engine".equals(spec.target())));
        assertTrue(specs.stream().allMatch(spec -> !spec.tiers().isEmpty()));
        assertTrue(specs.stream().allMatch(spec -> spec.source() != null && !spec.source().isBlank()));
    }

    @Test
    void libraryCoversCheatAndNonCheatCategories() {
        CheatCatalog catalog = new CheatCatalog();
        assertTrue(catalog.totalSpecs() >= 60, "全量样本不足: " + catalog.totalSpecs());
        assertTrue(catalog.cheatSamples().size() >= 40, "作弊样本不足: " + catalog.cheatSamples().size());
        assertTrue(catalog.allSamples().stream().allMatch(spec -> spec.sampleType() != null
                && !spec.sampleType().isBlank()));
        assertTrue(catalog.cheatSamples().stream().allMatch(spec -> spec.cheatType() != null));
        assertTrue(catalog.allSamples().stream().filter(spec -> !spec.isCheat())
                .allMatch(spec -> spec.cheatType() == null));

        int nonCheat = 0;
        for (SampleCategory category : SampleCategory.values()) {
            List<SampleSpec> specs = catalog.byCategory(category);
            if (category == SampleCategory.CHEAT) {
                assertFalse(specs.isEmpty());
                continue;
            }
            assertTrue(specs.size() >= 2, category + " 样本不足: " + specs.size());
            nonCheat += specs.size();
            for (SampleSpec spec : specs) {
                assertFalse(spec.isCheat());
                assertEquals(SampleTierName.values().length, spec.declaredTiers().size(), spec.id());
                assertTrue(spec.tiers().stream()
                        .allMatch(tier -> !tier.expected().expectedDetected()), spec.id());
            }
        }
        assertTrue(nonCheat >= 12, "通用异常样本不足: " + nonCheat);
        assertTrue(catalog.sampleById("anomaly.nan-coordinates.default").isPresent());
        assertTrue(catalog.byId("anomaly.nan-coordinates.default").isEmpty());
    }

    @Test
    void coversTwentyTwentySixQ4DetectionTargets() {
        CheatCatalog catalog = new CheatCatalog();

        for (String source : List.of("TruthfulAC", "Boar", "GrimAC")) {
            assertFalse(catalog.bySource(source).isEmpty(), source + " 缺少绕过样本");
        }
        assertTrue(catalog.allSamples().stream()
                .filter(spec -> spec.id().contains(".bypass"))
                .allMatch(spec -> spec.notes() != null && !spec.notes().isBlank()), "绕过样本必须写明来源与修复项");
    }

    @Test
    void everyBundledYamlParses() throws IOException {
        List<Path> files;
        try (Stream<Path> listing = Files.list(SAMPLE_DIR)) {
            files = listing.filter(path -> path.getFileName().toString().endsWith(".yaml")).toList();
        }
        assertFalse(files.isEmpty());
        for (Path file : files) {
            TestSpec[] specs = Json.readYaml(Files.readString(file, StandardCharsets.UTF_8), TestSpec[].class);
            assertTrue(specs.length > 0, file.getFileName().toString());
        }
    }

    @Test
    void directoryLoadMatchesBundledDefaults() {
        CheatCatalog catalog = new CheatCatalog();
        assertEquals(catalog.loadDefaults().size(), catalog.load(SAMPLE_DIR).size());
        assertEquals(catalog.totalSpecs(), catalog.loadAll(SAMPLE_DIR).size());
    }

    @Test
    void lookupByIdTypeAndSource() {
        CheatCatalog catalog = new CheatCatalog();
        assertTrue(catalog.byId("speed.wurst.default").isPresent());
        assertTrue(catalog.byId("missing.spec").isEmpty());
        assertFalse(catalog.byType(CheatType.XRAY).isEmpty());
        assertFalse(catalog.bySource("Wurst").isEmpty());
        assertTrue(catalog.bySource("Nope").isEmpty());
    }

    @Test
    void everySpecDeclaresABoundaryTier() {
        for (TestSpec spec : new CheatCatalog().loadDefaults()) {
            assertTrue(spec.tiers().stream()
                    .anyMatch(tier -> tier.expected() != null && tier.expected().boundary()), spec.id());
        }
    }
}
