package io.laplace.sprite.cheats;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.CheatType;
import io.laplace.sprite.core.model.TestSpec;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 样本目录：从 classpath 或文件系统加载声明式 YAML（第五章 TestSpec）。
 *
 * <p>目录同时承载检测类（CHEAT）与通用异常样本。{@link #loadDefaults()} 保持原有作弊口径，
 * 因为 {@code --suite cheat}、边界样本与既有验收都只认作弊子类；需要全量矩阵时用
 * {@link #allSamples()} / {@link #byCategory}。</p>
 */
public final class CheatCatalog {

    private static final String BUNDLED_DIR = "cheats/samples/";
    /** 内置样本文件清单；新增文件时同步登记，避免依赖 jar 内目录遍历。 */
    private static final List<String> BUNDLED_FILES = List.of(
            "movement.yaml", "combat.yaml", "world.yaml", "client.yaml", "generic.yaml", "datapack.yaml");

    private List<SampleSpec> defaults;
    /** 市场安装的额外样本；同 id 覆盖内置样本，便于社区修正。 */
    private final List<SampleSpec> extras;

    public CheatCatalog() {
        this(List.of());
    }

    public CheatCatalog(List<SampleSpec> extras) {
        this.extras = extras == null ? List.of() : List.copyOf(extras);
    }

    /** 全量样本（内置 + 市场安装），顺序与内置文件登记顺序一致。 */
    public synchronized List<SampleSpec> allSamples() {
        if (defaults == null) {
            Map<String, SampleSpec> merged = new LinkedHashMap<>();
            for (String file : BUNDLED_FILES) {
                for (SampleSpec sample : readResource(BUNDLED_DIR + file)) {
                    merged.put(sample.id(), sample);
                }
            }
            for (SampleSpec sample : extras) {
                merged.put(sample.id(), sample);
            }
            defaults = List.copyOf(merged.values());
        }
        return defaults;
    }

    /** 兼容入口：只返回作弊子类，供 {@code --suite cheat} 与边界样本使用。 */
    public List<TestSpec> loadDefaults() {
        return cheatSpecs();
    }

    public List<TestSpec> cheatSpecs() {
        return cheatSamples().stream().map(SampleSpec::spec).toList();
    }

    public List<SampleSpec> cheatSamples() {
        return allSamples().stream().filter(SampleSpec::isCheat).toList();
    }

    public List<SampleSpec> byCategory(SampleCategory category) {
        return allSamples().stream().filter(spec -> spec.category() == category).toList();
    }

    public int totalSpecs() {
        return allSamples().size();
    }

    /** 按目录加载；返回值同样只含作弊子类，保持既有调用口径。 */
    public List<TestSpec> load(Path directory) {
        return loadAll(directory).stream().filter(SampleSpec::isCheat).map(SampleSpec::spec).toList();
    }

    /** 按目录加载全量样本，供离线校验与测试使用。 */
    public List<SampleSpec> loadAll(Path directory) {
        try (Stream<Path> listing = Files.list(directory)) {
            List<Path> files = listing
                    .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .toList();
            List<SampleSpec> all = new ArrayList<>();
            for (Path file : files) {
                all.addAll(parse(Files.readString(file, StandardCharsets.UTF_8), file.getFileName().toString()));
            }
            return List.copyOf(all);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public Optional<SampleSpec> sampleById(String id) {
        return allSamples().stream().filter(spec -> Objects.equals(spec.id(), id)).findFirst();
    }

    /** 只查作弊样本：CLI 的 {@code --cheat} 参数不接受通用异常样本。 */
    public Optional<TestSpec> byId(String id) {
        return sampleById(id).filter(SampleSpec::isCheat).map(SampleSpec::spec);
    }

    public List<TestSpec> byType(CheatType type) {
        return cheatSpecs().stream().filter(spec -> spec.type() == type).toList();
    }

    public List<TestSpec> bySource(String source) {
        return cheatSpecs().stream().filter(spec -> Objects.equals(spec.source(), source)).toList();
    }

    private static List<SampleSpec> readResource(String resource) {
        try (InputStream in = CheatCatalog.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("缺少内置样本资源: " + resource);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), resource);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<SampleSpec> parse(String yaml, String origin) {
        SampleDocument[] documents = Json.readYaml(yaml, SampleDocument[].class);
        List<SampleSpec> specs = new ArrayList<>(documents.length);
        for (SampleDocument document : documents) {
            specs.add(toSample(document, origin));
        }
        return List.copyOf(specs);
    }

    private static SampleSpec toSample(SampleDocument document, String origin) {
        SampleCategory category = document.category();
        if (category == null) {
            // 旧文件没写 category 时按 type 推断为作弊样本，避免升级瞬间整库解析失败。
            if (document.type() == null) {
                throw new IllegalStateException(origin + " 的样本 " + document.id() + " 缺少 category");
            }
            category = SampleCategory.CHEAT;
        }
        CheatType cheatType = null;
        if (category == SampleCategory.CHEAT) {
            if (document.type() == null) {
                throw new IllegalStateException("作弊样本 " + document.id() + " 缺少 type");
            }
            cheatType = CheatType.valueOf(document.type());
        } else if (document.type() != null) {
            throw new IllegalStateException("非作弊样本 " + document.id() + " 不应声明 type: " + document.type());
        }
        String sampleType = document.sampleType() != null && !document.sampleType().isBlank()
                ? document.sampleType()
                : cheatType == null ? null : cheatType.name();
        if (sampleType == null) {
            throw new IllegalStateException("样本 " + document.id() + " 缺少 sampleType");
        }
        return new SampleSpec(document.id(), document.name(), category, sampleType, cheatType,
                document.target(), document.tiers(), document.baseline(), document.injector(),
                document.source(), document.notes());
    }
}
