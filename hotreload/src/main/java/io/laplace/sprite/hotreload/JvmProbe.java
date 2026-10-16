package io.laplace.sprite.hotreload;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * JVM 探测（15.2）：从可配置输入（属性表 / {@code java -version} 输出）判出 JVM 类型。
 *
 * <p>输入全部由调用方给，探测自身不碰进程，离线与 CI 都能稳定复现。</p>
 */
public final class JvmProbe {

    /** 显式覆盖键：属性里写了就以它为准。 */
    public static final String KIND_PROPERTY = "laplace.jvm.kind";

    /** 启动参数输入键：用于判断增强类重定义是否已经打开。 */
    public static final String ARGS_PROPERTY = "laplace.jvm.args";

    public static final String ENHANCED_REDEFINITION_FLAG = "-XX:+AllowEnhancedClassRedefinition";

    private final Map<String, String> inputs;
    private final String javaVersionOutput;

    public JvmProbe(Map<String, String> inputs, String javaVersionOutput) {
        this.inputs = inputs == null ? Map.of() : Map.copyOf(inputs);
        this.javaVersionOutput = javaVersionOutput == null ? "" : javaVersionOutput;
    }

    public static JvmProbe of(Map<String, String> inputs, String javaVersionOutput) {
        return new JvmProbe(inputs, javaVersionOutput);
    }

    /** 当前 JVM：只带系统属性；java -version 输出由调用方按需补。 */
    public static JvmProbe system() {
        Map<String, String> properties = new LinkedHashMap<>();
        System.getProperties().stringPropertyNames()
                .forEach(name -> properties.put(name, System.getProperty(name)));
        return new JvmProbe(properties, "");
    }

    public Map<String, String> inputs() {
        return inputs;
    }

    public JvmKind kind() {
        String explicit = inputs.get(KIND_PROPERTY);
        if (explicit != null && !explicit.isBlank()) {
            return parse(explicit);
        }
        String evidence = (value("java.vm.name") + " " + value("java.vendor") + " " + value("java.version")
                + " " + javaVersionOutput).toLowerCase(Locale.ROOT);
        if (evidence.contains("dcevm")) {
            return JvmKind.DCEVM;
        }
        if (evidence.contains("jetbrains") || evidence.contains("jbr")) {
            return JvmKind.JETBRAINS_RUNTIME;
        }
        if (evidence.contains("hotspot") || evidence.contains("openjdk") || evidence.contains("java(tm)")) {
            return JvmKind.HOTSPOT;
        }
        return JvmKind.UNKNOWN;
    }

    public boolean enhancedRedefinitionRequested() {
        return value(ARGS_PROPERTY).contains(ENHANCED_REDEFINITION_FLAG);
    }

    /** 增强类重定义可用：JVM 本身支持，或启动参数里已显式带上。 */
    public boolean supportsEnhancedRedefinition() {
        return kind().supportsEnhancedRedefinition() || enhancedRedefinitionRequested();
    }

    public String description() {
        return "java.vm.name=%s, java.version=%s".formatted(value("java.vm.name"), value("java.version"));
    }

    private String value(String key) {
        return inputs.getOrDefault(key, "");
    }

    private static JvmKind parse(String raw) {
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return JvmKind.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("未知 JVM 类型: " + raw);
        }
    }
}
