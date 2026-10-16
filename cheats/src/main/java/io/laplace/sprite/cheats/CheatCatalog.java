package io.laplace.sprite.cheats;

import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatType;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 作弊样本目录：从 classpath 或文件系统加载声明式 YAML（第五章 CheatSpec）。
 *
 * <p>每个 YAML 文件的顶层是一个 CheatSpec 序列。</p>
 */
public final class CheatCatalog {

    private static final String BUNDLED_DIR = "cheats/samples/";
    /** 内置样本文件清单；新增文件时同步登记，避免依赖 jar 内目录遍历。 */
    private static final List<String> BUNDLED_FILES = List.of(
            "movement.yaml", "combat.yaml", "world.yaml", "client.yaml");

    private List<CheatSpec> defaults;

    public synchronized List<CheatSpec> loadDefaults() {
        if (defaults == null) {
            List<CheatSpec> all = new ArrayList<>();
            for (String file : BUNDLED_FILES) {
                all.addAll(readResource(BUNDLED_DIR + file));
            }
            defaults = List.copyOf(all);
        }
        return defaults;
    }

    public List<CheatSpec> load(Path directory) {
        try (Stream<Path> listing = Files.list(directory)) {
            List<Path> files = listing
                    .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .toList();
            List<CheatSpec> all = new ArrayList<>();
            for (Path file : files) {
                all.addAll(parse(Files.readString(file, StandardCharsets.UTF_8)));
            }
            return List.copyOf(all);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public Optional<CheatSpec> byId(String id) {
        return loadDefaults().stream().filter(spec -> Objects.equals(spec.id(), id)).findFirst();
    }

    public List<CheatSpec> byType(CheatType type) {
        return loadDefaults().stream().filter(spec -> spec.type() == type).toList();
    }

    public List<CheatSpec> bySource(String source) {
        return loadDefaults().stream().filter(spec -> Objects.equals(spec.source(), source)).toList();
    }

    private static List<CheatSpec> readResource(String resource) {
        try (InputStream in = CheatCatalog.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("缺少内置作弊样本资源: " + resource);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<CheatSpec> parse(String yaml) {
        return List.of(Json.readYaml(yaml, CheatSpec[].class));
    }
}
