package io.laplace.sprite.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mixin 启动日志扫描（15.11 P0）：把注入异常翻成结构化的 {@link InjectionFailure}。
 *
 * <p>只认三类真实日志形态：{@code InvalidInjectionException}、{@code Injection point ... is not valid}、
 * {@code @Inject ... cannot be applied}。其他含 mixin 字样的普通日志不进失败列表，
 * 否则门禁会被 INFO 噪音打穿。</p>
 */
public final class LaunchLogScanner {

    private static final Pattern MIXIN_CONFIG_MOD = Pattern.compile("([A-Za-z0-9_.-]+)\\.mixins(?:\\.json)?\\b");

    private static final Pattern BRACKET_MOD = Pattern.compile("^\\[([A-Za-z0-9_.-]+)]\\s");

    private static final Pattern DESCRIPTOR_CLASS = Pattern.compile("L([A-Za-z0-9_$/]+);");

    private static final Pattern TARGET_AFTER_KEYWORD = Pattern.compile(
            "\\b(?:in|to|target|class)\\s+((?:[A-Za-z_$][\\w$]*\\.)+[A-Za-z_$][\\w$]*)");

    private static final Pattern DOTTED_UPPER = Pattern.compile("(?:[A-Za-z_$][\\w$]*\\.)+[A-Z][\\w$]*");

    private static final Pattern INJECTION_POINT = Pattern.compile("Injection point.*is not valid",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern CANNOT_APPLY = Pattern.compile(
            "@(Inject|Redirect|ModifyVariable|Overwrite).*cannot be applied", Pattern.CASE_INSENSITIVE);

    private static final List<String> NOISE_PREFIXES = List.of("org.spongepowered.", "java.", "jdk.", "com.google.");

    private LaunchLogScanner() {
    }

    /** 扫描整份启动日志；没有失败行时返回空列表。 */
    public static List<InjectionFailure> scan(String launchLog) {
        if (launchLog == null || launchLog.isBlank()) {
            return List.of();
        }
        List<InjectionFailure> failures = new ArrayList<>();
        for (String rawLine : launchLog.split("\\R")) {
            if (!isFailureLine(rawLine)) {
                continue;
            }
            failures.add(new InjectionFailure(modIdOf(rawLine), targetClassOf(rawLine), kindOf(rawLine),
                    rawLine.trim(), failureReason(rawLine)));
        }
        return List.copyOf(failures);
    }

    /** 日志行是否属于三类注入失败。 */
    static boolean isFailureLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.contains("invalidinjectionexception")
                || INJECTION_POINT.matcher(line).find()
                || CANNOT_APPLY.matcher(line).find();
    }

    /** 模组 id：优先取 {@code <modid>.mixins.json} 配置名，其次取行首 {@code [modid]} 前缀。 */
    static String modIdOf(String line) {
        Matcher config = MIXIN_CONFIG_MOD.matcher(line);
        if (config.find()) {
            return config.group(1);
        }
        Matcher bracket = BRACKET_MOD.matcher(line);
        if (bracket.find()) {
            return bracket.group(1);
        }
        return InjectionFailure.UNKNOWN;
    }

    private static String targetClassOf(String line) {
        Matcher descriptor = DESCRIPTOR_CLASS.matcher(line);
        if (descriptor.find()) {
            return descriptor.group(1).replace('/', '.');
        }
        Matcher keyword = TARGET_AFTER_KEYWORD.matcher(line);
        if (keyword.find()) {
            return keyword.group(1);
        }
        // 兜底只在没有更明确线索时启用：日志里同时出现框架包名时优先跳过它们。
        Matcher dotted = DOTTED_UPPER.matcher(line);
        String first = null;
        while (dotted.find()) {
            String candidate = dotted.group();
            if (first == null) {
                first = candidate;
            }
            if (NOISE_PREFIXES.stream().noneMatch(candidate::startsWith)) {
                return candidate;
            }
        }
        return first == null ? InjectionFailure.UNKNOWN : first;
    }

    private static MixinInjectionKind kindOf(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        for (MixinInjectionKind kind : MixinInjectionKind.values()) {
            if (kind == MixinInjectionKind.UNKNOWN) {
                continue;
            }
            if (lower.contains(kind.annotation().toLowerCase(Locale.ROOT))) {
                return kind;
            }
        }
        return MixinInjectionKind.UNKNOWN;
    }

    private static String failureReason(String line) {
        String trimmed = line.trim();
        if (INJECTION_POINT.matcher(trimmed).find()) {
            return "注入点不匹配：MC 小版本映射漂移导致声明的注入点失效。原始日志：" + trimmed;
        }
        if (CANNOT_APPLY.matcher(trimmed).find()) {
            return "注入无法应用：目标方法/字段不存在或签名已变化。原始日志：" + trimmed;
        }
        return "Mixin 注入抛 InvalidInjectionException，启动期即失败。原始日志：" + trimmed;
    }
}
