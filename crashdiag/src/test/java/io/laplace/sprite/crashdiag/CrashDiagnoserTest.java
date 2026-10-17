package io.laplace.sprite.crashdiag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 分类路由与产出（15.5 / 15.8）：各分支各走一遍，外加签名顺序。 */
class CrashDiagnoserTest {

    private static final String MIXIN_LOG = """
            [main/ERROR]: Mixin apply failed questmod.mixins.json:QuestMixin -> net.minecraft.server.MinecraftServer
            org.spongepowered.asm.mixin.throwables.MixinApplyError: Mixin apply failed
            \tat com.example.questmod.QuestManager.tick(QuestManager.java:42)
            \tat com.example.questmod.QuestManager.loop(QuestManager.java:18)
            \tat net.minecraft.server.MinecraftServer.tick(MinecraftServer.java:800)
            """;

    private static final String DEPENDENCY_LOG = """
            Incompatible mod set!
            net.fabricmc.loader.impl.FormattedException: Mod resolution encountered an incompatible mod set!
            Unmet dependency listing:
            \t - Mod 'Foo' (foo) 1.0.0 requires version 1.2.0 or later of mod 'bar' (bar), which is missing!
            \t - Mod 'Foo' (foo) 1.0.0 requires version 0.5.0 or later of mod 'baz' (baz), but 0.4.0 is present
            \tat net.fabricmc.loader.impl.discovery.ModResolver.resolve(ModResolver.java:200)
            """;

    private static final String OOM_LOG = """
            java.lang.OutOfMemoryError: Java heap space
            \tat com.example.perfcore.Cache.grow(Cache.java:77)
            \tat com.example.perfcore.Cache.put(Cache.java:31)
            \tat net.minecraft.server.MinecraftServer.tick(MinecraftServer.java:800)
            """;

    private static final String NATIVE_LOG = """
            # A fatal error has been detected by the Java Runtime Environment:
            #  SIGSEGV (0xb) at pc=0x00007f, pid=4321, tid=4322
            """;

    private static final String PACK_JSON_LOG = """
            [Server thread/ERROR]: Failed to parse world/datapacks/broken/pack.mcmeta
            com.google.gson.JsonSyntaxException: com.google.gson.stream.MalformedJsonException: \
            Unterminated object at line 1 column 45 path $.pack.description
            \tat net.minecraft.server.packs.PackResources.getMetadata(PackResources.java:120)
            """;

    private static final String PACK_MISSING_LOG = """
            [Server thread/ERROR]: java.lang.IllegalStateException: \
            Couldn't find pack meta in world/datapacks/broken
            """;

    private static final String PACK_WORLD_LOAD_LOG = """
            [Render thread/ERROR]: Failed to load datapacks, can't proceed with server load
            com.google.gson.JsonSyntaxException: com.google.gson.stream.MalformedJsonException: \
            Expected value at line 1 column 2 path $
            """;

    @TempDir
    Path tempDir;

    private final CrashDiagnoser diagnoser = new CrashDiagnoser(ClassIndex.defaultIndex());

    @Test
    void routesMixinConflict() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst", withoutHsErr(), MIXIN_LOG);

        assertEquals(CrashCategory.MIXIN_CONFLICT, diagnosis.category());
        assertEquals("mixin-apply-failed", diagnosis.signature());
        assertEquals("questmod", diagnosis.suspects().get(0).modId());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("mixins.json")));
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("questmod")));
    }

    @Test
    void routesDependencyMismatchAndIncludesVersionDiff() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst", withoutHsErr(), DEPENDENCY_LOG);

        assertEquals(CrashCategory.DEPENDENCY_MISMATCH, diagnosis.category());
        assertEquals("incompatible-mod-set", diagnosis.signature());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("要求≥1.2.0")),
                "缺失依赖要写进建议: " + diagnosis.humanAdvice());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("要求≥0.5.0")
                        && advice.contains("实装 0.4.0")),
                "实装版本差要写进建议: " + diagnosis.humanAdvice());
    }

    @Test
    void routesJvmNativeCrash() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst", withHsErr(), NATIVE_LOG);

        assertEquals(CrashCategory.JVM_NATIVE, diagnosis.category());
        assertEquals("hs-err-native", diagnosis.signature());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("SIGSEGV")));
    }

    @Test
    void hsErrWinsOverOomMentionInLog() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst", withHsErr(), OOM_LOG);

        assertEquals(CrashCategory.JVM_NATIVE, diagnosis.category(),
                "现场有 hs_err_pid 时必须优先判原生崩溃，不能因为日志提到 OOM 就改判");
        assertEquals("hs-err-native", diagnosis.signature());
    }

    @Test
    void routesOutOfMemoryWithHeapGcSummary() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst", withoutHsErr(), OOM_LOG,
                new HeapGcSummary(3900, 4096, 45.0, 220.0));

        assertEquals(CrashCategory.OUT_OF_MEMORY, diagnosis.category());
        assertEquals("out-of-memory", diagnosis.signature());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("堆 3900MB / 4096MB")),
                "内存分支要带上剖析轨的堆 / GC 数据: " + diagnosis.humanAdvice());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("GC p99 45.0ms")));
    }

    @Test
    void memoryBranchWithoutProfileDataSaysSo() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst", withoutHsErr(), OOM_LOG);

        assertEquals(CrashCategory.OUT_OF_MEMORY, diagnosis.category());
        assertTrue(diagnosis.humanAdvice().stream()
                .anyMatch(advice -> advice.contains("未提供堆 / GC 摘要")));
    }

    @Test
    void unknownWhenNoSignatureMatches() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst", withoutHsErr(), "nothing familiar here");

        assertEquals(CrashCategory.UNKNOWN, diagnosis.category());
        assertEquals("unmatched", diagnosis.signature());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("问题现场包")));
    }

    @Test
    void signatureOrderingIsExplicitAndStable() {
        List<CrashSignature> ordered = CrashSignature.ordered();
        assertEquals(List.of("hs-err-native", "out-of-memory", "pack-world-load-failure",
                        "pack-meta-json-syntax", "pack-meta-missing", "incompatible-mod-set",
                        "mixin-apply-failed"),
                ordered.stream().map(CrashSignature::id).toList());
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(ordered.get(i - 1).order() < ordered.get(i).order());
        }
        assertTrue(ordered.get(0).requiresNativeFile(), "第一条看 hs_err 文件而不是日志文本");
        assertTrue(ordered.get(0).rationale().contains("优先"));
    }

    @Test
    void routesDatapackJsonSyntaxErrorAndExtractsFileAndPath() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst-pack", withoutHsErr(), PACK_JSON_LOG);

        assertEquals(CrashCategory.RESOURCE_PACK_LOAD_FAILURE, diagnosis.category());
        assertEquals("pack-meta-json-syntax", diagnosis.signature());
        assertEquals("MC-123460", diagnosis.mojiraId());
        assertEquals("world/datapacks/broken/pack.mcmeta", diagnosis.packFile());
        assertEquals("$.pack.description", diagnosis.jsonPath());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("$.pack.description")),
                "文件与 JSON 路径要进建议: " + diagnosis.humanAdvice());
        assertTrue(diagnosis.humanAdvice().stream().anyMatch(advice -> advice.contains("MC-123460")));
    }

    @Test
    void routesMissingPackMeta() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst-pack", withoutHsErr(), PACK_MISSING_LOG);

        assertEquals(CrashCategory.RESOURCE_PACK_LOAD_FAILURE, diagnosis.category());
        assertEquals("pack-meta-missing", diagnosis.signature());
        assertEquals("MC-123459", diagnosis.mojiraId());
        assertEquals("world/datapacks/broken", diagnosis.packFile());
        assertEquals("$.pack", diagnosis.jsonPath());
    }

    @Test
    void routesWorldLoadFailureFromUnparsablePack() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst-pack", withoutHsErr(), PACK_WORLD_LOAD_LOG);

        assertEquals(CrashCategory.RESOURCE_PACK_LOAD_FAILURE, diagnosis.category());
        assertEquals("pack-world-load-failure", diagnosis.signature());
        assertEquals("MC-310240", diagnosis.mojiraId());
    }

    @Test
    void jsonSyntaxWithoutPackContextStaysUnknown() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst", withoutHsErr(),
                "com.google.gson.JsonSyntaxException: MalformedJsonException: bad config");

        assertEquals(CrashCategory.UNKNOWN, diagnosis.category(),
                "没有 pack 上下文的 JSON 异常可能来自模组配置，不能归到数据包分类");
    }

    @Test
    void hsErrWinsOverPackJsonError() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst-pack", withHsErr(), PACK_JSON_LOG);

        assertEquals(CrashCategory.JVM_NATIVE, diagnosis.category(),
                "现场有 hs_err_pid 时仍判原生崩溃，包 JSON 特征不得抢先");
        assertEquals("hs-err-native", diagnosis.signature());
        assertNull(diagnosis.mojiraId());
    }

    @Test
    void resourcePackCategoryIsRenderedInJsonAndMarkdown() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst-pack", withoutHsErr(), PACK_JSON_LOG);
        DiagnosisRenderer renderer = new DiagnosisRenderer(diagnosis);

        String json = renderer.toJson();
        assertTrue(json.contains("RESOURCE_PACK_LOAD_FAILURE"));
        assertTrue(json.contains("\"mojiraId\""));
        assertTrue(json.contains("MC-123460"));
        assertTrue(json.contains("\"jsonPath\""));
        assertTrue(json.contains("$.pack.description"));
        assertTrue(json.contains("\"packFile\""));

        String markdown = renderer.toMarkdown();
        assertTrue(markdown.contains("资源包 / 数据包加载失败"));
        assertTrue(markdown.contains("Mojira 编号：MC-123460"));
        assertTrue(markdown.contains("JSON 路径：$.pack.description"));
    }

    @Test
    void classifyMatchesSignatureTable() {
        assertEquals(CrashCategory.JVM_NATIVE, CrashDiagnoser.classify(OOM_LOG, true));
        assertEquals(CrashCategory.OUT_OF_MEMORY, CrashDiagnoser.classify(OOM_LOG, false));
        assertEquals(CrashCategory.MIXIN_CONFLICT, CrashDiagnoser.classify(MIXIN_LOG, false));
        assertEquals(CrashCategory.DEPENDENCY_MISMATCH, CrashDiagnoser.classify(DEPENDENCY_LOG, false));
        assertEquals(CrashCategory.UNKNOWN, CrashDiagnoser.classify("", false));
    }

    @Test
    void versionDiffsAreParsedLineByLine() {
        List<VersionDiff> diffs = CrashDiagnoser.versionDiffs(DEPENDENCY_LOG);

        assertEquals(2, diffs.size());
        assertEquals("bar", diffs.get(0).dependency());
        assertEquals("1.2.0", diffs.get(0).requiredVersion());
        assertTrue(diffs.get(0).missing());
        assertEquals("bar：要求≥1.2.0，当前未找到", diffs.get(0).describe());

        assertEquals("baz", diffs.get(1).dependency());
        assertFalse(diffs.get(1).missing());
        assertEquals("baz：要求≥0.5.0，实装 0.4.0", diffs.get(1).describe());
        assertTrue(CrashDiagnoser.versionDiffs("no requirements").isEmpty());
    }

    @Test
    void diagnosisIsRenderedAsJsonAndChineseMarkdown() {
        CrashDiagnosis diagnosis = diagnoser.diagnose("inst-md", withoutHsErr(), MIXIN_LOG);
        DiagnosisRenderer renderer = new DiagnosisRenderer(diagnosis);

        String json = renderer.toJson();
        assertTrue(json.contains("MIXIN_CONFLICT"));
        assertTrue(json.contains("mixin-apply-failed"));
        assertTrue(json.contains("\"humanAdvice\""));
        assertTrue(json.contains("\"classIndexCoverage\""));

        String markdown = renderer.toMarkdown();
        assertTrue(markdown.contains("## 崩溃诊断（15.5）"));
        assertTrue(markdown.contains("Mixin 与类冲突"));
        assertTrue(markdown.contains("### 嫌疑 mod（按帧命中数排序）"));
        assertTrue(markdown.contains("### 建议"));
        assertTrue(markdown.contains("### 现场采集"));
        assertEquals(diagnosis.humanAdvice(), renderer.toHumanAdvice());
        assertFalse(diagnosis.humanAdvice().isEmpty());
    }

    private CrashArtifacts withoutHsErr() {
        return new CrashCollector().collect(tempDir);
    }

    private CrashArtifacts withHsErr() {
        try {
            Files.writeString(tempDir.resolve("hs_err_pid4321.log"), NATIVE_LOG);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return new CrashCollector().collect(tempDir);
    }
}
