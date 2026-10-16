package io.laplace.sprite.core.io;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON / YAML 读写（第五章：CheatSpec、LegitTrajectory 等声明式定义）。
 */
public final class Json {

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private Json() {
    }

    public static ObjectMapper jsonMapper() {
        return JSON;
    }

    public static ObjectMapper yamlMapper() {
        return YAML;
    }

    public static String write(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static String writeYaml(Object value) {
        try {
            return YAML.writeValueAsString(value);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static <T> T read(String content, Class<T> type) {
        try {
            return JSON.readValue(content, type);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static <T> T readYaml(String content, Class<T> type) {
        try {
            return YAML.readValue(content, type);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static <T> T readFile(Path path, Class<T> type) {
        try {
            return read(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static void writeFile(Path path, Object value) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, write(value), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
