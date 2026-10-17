package io.laplace.sprite.crashdiag;

import io.laplace.sprite.crashdiag.CrashArtifacts.CollectedFile;
import io.laplace.sprite.crashdiag.FrameAttributor.Attribution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 崩溃诊断器（15.5）：特征签名路由 + 栈帧归因 + 人话建议。
 *
 * <p>签名顺序由 {@link CrashSignature#ordered()} 固定：hs_err_pid 文件优先于日志文本，
 * 所以「日志里同时出现 OOM 但现场有 hs_err_pid」会被判成 JVM 原生崩溃。</p>
 */
public final class CrashDiagnoser {

    /** Fabric / Quilt 加载器给出的依赖要求措辞。 */
    private static final Pattern REQUIRES_OR_LATER =
            Pattern.compile("requires version ([0-9][A-Za-z0-9.+\\-]*) or later of mod '([^']+)'");

    /** 同一行里的实装版本措辞。 */
    private static final Pattern INSTALLED_PRESENT =
            Pattern.compile("but ([0-9][A-Za-z0-9.+\\-]*) is present");

    /** 报错行里的 pack.mcmeta 路径。 */
    private static final Pattern PACK_META_FILE = Pattern.compile("([\\w./\\\\:-]*pack\\.mcmeta)");

    /** MC-123459 的措辞：Couldn't find pack meta in <目录>。 */
    private static final Pattern PACK_META_IN = Pattern.compile("pack meta in\\s+([^\\s,;)]+)",
            Pattern.CASE_INSENSITIVE);

    /** Gson / Jackson 报错里的 JSON 路径。 */
    private static final Pattern JSON_PATH = Pattern.compile("\\$(?:\\.[A-Za-z0-9_]+|\\[[0-9]+])+");

    private final ClassIndex classIndex;
    private final FrameAttributor attributor = new FrameAttributor();

    public CrashDiagnoser() {
        this(ClassIndex.defaultIndex());
    }

    public CrashDiagnoser(ClassIndex classIndex) {
        this.classIndex = Objects.requireNonNull(classIndex, "classIndex");
    }

    public ClassIndex classIndex() {
        return classIndex;
    }

    /** 不带堆 / GC 摘要的诊断。 */
    public CrashDiagnosis diagnose(String instanceId, CrashArtifacts artifacts, String text) {
        return diagnose(instanceId, artifacts, text, null);
    }

    /**
     * 完整诊断。
     *
     * @param heapGc 剖析轨的堆 / GC 摘要，仅内存分支使用；可为 null
     */
    public CrashDiagnosis diagnose(String instanceId, CrashArtifacts artifacts, String text,
                                   HeapGcSummary heapGc) {
        String content = text == null ? "" : text;
        boolean hsErrPresent = artifacts != null && artifacts.hsErrPresent();
        Optional<CrashSignature> matched = CrashSignature.firstMatch(content, hsErrPresent);
        CrashCategory category = matched.map(CrashSignature::category).orElse(CrashCategory.UNKNOWN);
        String signature = matched.map(CrashSignature::id).orElse("unmatched");

        Attribution attribution = attributor.attribute(content, classIndex);
        List<VersionDiff> diffs = category == CrashCategory.DEPENDENCY_MISMATCH
                ? versionDiffs(content)
                : List.of();
        PackFailure packFailure = category == CrashCategory.RESOURCE_PACK_LOAD_FAILURE
                ? packFailure(content, signature).orElse(null)
                : null;

        List<String> advice = new ArrayList<>(adviceFor(category, attribution, diffs, heapGc, packFailure));
        advice.addAll(attribution.warnings());

        return new CrashDiagnosis("diag-" + UUID.randomUUID(), instanceId, category, signature,
                attribution.suspects(), attribution.classIndexCoverage(), advice, artifacts, Instant.now(),
                packFailure == null ? null : packFailure.file(),
                packFailure == null ? null : packFailure.jsonPath(),
                packFailure == null ? null : packFailure.mojiraId());
    }

    /**
     * 从日志里提取包失败的文件与 JSON 路径（15.8）。
     *
     * <p>Mojira 编号由签名 id 决定：世界加载失败 MC-310240、reload 解析异常 MC-123460、
     * 找不到 pack.mcmeta MC-123459。路径优先取报错文本里现成的 {@code $.xxx}；没有就按签名给默认值。</p>
     */
    public static Optional<PackFailure> packFailure(String text, String signatureId) {
        String mojiraId = mojiraId(signatureId);
        if (mojiraId == null || text == null || text.isBlank()) {
            return Optional.empty();
        }
        String file = packFile(text);
        String jsonPath = jsonPath(text);
        if (jsonPath == null) {
            jsonPath = "pack-meta-missing".equals(signatureId) ? "$.pack" : "$";
        }
        return Optional.of(new PackFailure(mojiraId, file, jsonPath));
    }

    private static String mojiraId(String signatureId) {
        if (signatureId == null) {
            return null;
        }
        return switch (signatureId) {
            case "pack-world-load-failure" -> "MC-310240";
            case "pack-meta-json-syntax" -> "MC-123460";
            case "pack-meta-missing" -> "MC-123459";
            default -> null;
        };
    }

    private static String packFile(String text) {
        Matcher file = PACK_META_FILE.matcher(text);
        if (file.find()) {
            return trimToken(file.group(1));
        }
        Matcher directory = PACK_META_IN.matcher(text);
        if (directory.find()) {
            return trimToken(directory.group(1));
        }
        return "pack.mcmeta";
    }

    private static String jsonPath(String text) {
        Matcher path = JSON_PATH.matcher(text);
        return path.find() ? path.group() : null;
    }

    private static String trimToken(String raw) {
        String trimmed = raw;
        while (!trimmed.isEmpty() && "\\\"'<>,;()[]".indexOf(trimmed.charAt(0)) >= 0) {
            trimmed = trimmed.substring(1);
        }
        while (!trimmed.isEmpty() && "\\\"'<>,;()[]".indexOf(trimmed.charAt(trimmed.length() - 1)) >= 0) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "pack.mcmeta" : trimmed;
    }

    /** 只做分类，方便按签名顺序写测试。 */
    public static CrashCategory classify(String text, boolean hsErrPresent) {
        return CrashSignature.firstMatch(text, hsErrPresent)
                .map(CrashSignature::category)
                .orElse(CrashCategory.UNKNOWN);
    }

    /**
     * 从「Incompatible mod set」日志里抽依赖版本差。
     *
     * <p>只认 Fabric / Quilt 加载器给出的
     * {@code requires version X or later of mod 'Y'} 措辞，以及同一行里的
     * {@code but Z is present} 实装版本；解析不到就是空列表，不猜。</p>
     */
    public static List<VersionDiff> versionDiffs(String text) {
        Map<String, VersionDiff> diffs = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        for (String line : text.split("\\R")) {
            Matcher requires = REQUIRES_OR_LATER.matcher(line);
            if (!requires.find()) {
                continue;
            }
            String requiredVersion = requires.group(1);
            String dependency = requires.group(2);
            String installedVersion = null;
            Matcher present = INSTALLED_PRESENT.matcher(line);
            if (present.find()) {
                installedVersion = present.group(1);
            }
            diffs.putIfAbsent(dependency, new VersionDiff(dependency, requiredVersion, installedVersion));
        }
        return List.copyOf(diffs.values());
    }

    private static List<String> adviceFor(CrashCategory category, Attribution attribution,
                                          List<VersionDiff> diffs, HeapGcSummary heapGc,
                                          PackFailure packFailure) {
        List<String> advice = new ArrayList<>();
        String primary = attribution.primarySuspect().orElse("未解析出嫌疑 mod");
        switch (category) {
            case MIXIN_CONFLICT -> {
                advice.add("疑似 Mixin / 类冲突：先查栈中首个 modid「%s」的注入目标是否与服务端版本对得上"
                        .formatted(primary));
                advice.add("打开该 mod 的 mixins.json，核对 target 类是否被改名或已被其它 mod 覆盖");
                advice.add("把「%s」升级或回退到与该 Minecraft 版本匹配的构建".formatted(primary));
            }
            case DEPENDENCY_MISMATCH -> {
                if (diffs.isEmpty()) {
                    advice.add("日志命中 Incompatible mod set，但没解析出「要求 X or later / 实装 Y」措辞的依赖行，"
                            + "需人工核对 mod 清单");
                } else {
                    diffs.forEach(diff -> advice.add("调整依赖：" + diff.describe()));
                }
                advice.add("改 mod-manifest.txt 或 packwiz 声明里的版本后重新解析依赖树");
            }
            case JVM_NATIVE -> {
                advice.add("JVM 原生崩溃（hs_err_pid），不是插件业务异常：先看 hs_err 头部是 SIGSEGV 还是"
                        + " EXCEPTION_ACCESS_VIOLATION");
                advice.add("排查原生库与 JVM 参数（JNI、-XX 开关、内存映射），必要时换 JDK 版本复现");
                advice.add("原生崩溃常伴随内存耗尽，若堆栈同时出现 OOM，与内存分支一并看");
            }
            case OUT_OF_MEMORY -> {
                advice.add("内存不足：%s".formatted(heapGc == null
                        ? "未提供堆 / GC 摘要（剖析轨数据未接入）"
                        : heapGc.describe()));
                advice.add("调大 -Xmx，或按剖析轨的堆与 GC p99/max 曲线区分配额不足与泄漏");
                advice.add("落 heap dump 后按支配树定位持有者，重点看区块缓存与玩家数据");
            }
            case RESOURCE_PACK_LOAD_FAILURE -> {
                if (packFailure == null) {
                    advice.add("资源包 / 数据包加载失败，但没从日志里提取到文件名与 JSON 路径，"
                            + "请打开问题现场包逐行核对");
                } else {
                    advice.add("出错文件 %s 的 %s 解析失败，对应 %s"
                            .formatted(packFailure.file(), packFailure.jsonPath(), packFailure.mojiraId()));
                    advice.add("用 JSON 校验器打开该 pack.mcmeta，按上述路径修语法；JSON 不允许注释与尾逗号");
                }
                advice.add("修好后先 /reload 验证；若世界已经进不去，先把该包移出 datapacks/ 再启动");
            }
            case UNKNOWN -> advice.add("未命中已登记的特征签名，请打开问题现场包人工逐段查看");
        }
        return advice;
    }

    /** 现场包里每条采集记录的统一格式，供渲染与导出复用。 */
    static List<Map<String, Object>> describeArtifacts(CrashArtifacts artifacts) {
        List<Map<String, Object>> described = new ArrayList<>();
        if (artifacts == null) {
            return described;
        }
        for (CollectedFile file : artifacts.files()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("kind", file.kind());
            entry.put("path", file.path().toString());
            entry.put("found", file.found());
            described.add(entry);
        }
        return described;
    }
}
