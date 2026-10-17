package io.laplace.sprite.datapack;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 资源包解析层冒烟（15.8）：解析 server.properties 的 resource-pack 指向的 pack.mcmeta。
 *
 * <p>只做到元数据解析与 sha1 校验。客户端材质解析不在无头环境验证：贴图能不能被客户端接受
 * 需要真实渲染管线，这里不下载远端资源包，也不猜材质是否可用。</p>
 */
public final class ResourcePackResolver {

    /**
     * 加载判定。
     *
     * @param loadable  是否可以交给服务端下发
     * @param metaParsed 是否真的解析过 pack.mcmeta
     * @param source     实际解析的来源（路径或 URL）
     * @param meta       解析成功时的元数据
     * @param error      解析失败时的结构化错误
     * @param sha1Actual 实际算出的 SHA-1；未计算时为 null
     * @param notes      附注
     */
    public record LoadVerdict(boolean loadable, boolean metaParsed, String source, PackMeta meta,
                              PackMetaError error, String sha1Actual, List<String> notes) {

        public LoadVerdict {
            notes = notes == null ? List.of() : List.copyOf(notes);
        }
    }

    private static final String ZIP_META_ENTRY = PackMetaParser.FILE_NAME;

    private final PackMetaParser parser;

    public ResourcePackResolver() {
        this(new PackMetaParser());
    }

    public ResourcePackResolver(PackMetaParser parser) {
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    public LoadVerdict resolve(ResourcePackSpec spec) {
        Objects.requireNonNull(spec, "spec");
        List<String> notes = new ArrayList<>();

        if (spec.packMeta() != null) {
            notes.add("pack.mcmeta 由调用方提供，未重新读取内容");
            if (spec.remote()) {
                notes.add("远端 URL 未下载；材质解析不在无头环境验证");
            }
            return new LoadVerdict(true, true, spec.url(), spec.packMeta(), null, null, notes);
        }
        if (spec.remote()) {
            notes.add("远端资源包未下载：无头环境只校验 URL 形式，pack.mcmeta 未解析");
            notes.add("客户端材质解析不在无头环境验证");
            return new LoadVerdict(false, false, spec.url(), null, null, null, notes);
        }

        Path path = localPath(spec.url());
        if (!Files.exists(path)) {
            PackMetaError error = new PackMetaError(path.toString(), "$.pack",
                    PackMetaErrorKind.MISSING_META, "Couldn't find pack meta in " + path,
                    PackMetaParser.MOJIRA_MISSING_META);
            notes.add("资源包路径不存在");
            return new LoadVerdict(false, false, path.toString(), null, error, null, notes);
        }
        if (Files.isRegularFile(path) && spec.sha1().length() > 0) {
            String actual = sha1(path);
            if (!actual.equals(spec.sha1())) {
                notes.add("sha1 不一致：声明 %s，实际 %s".formatted(spec.sha1(), actual));
                return new LoadVerdict(false, false, path.toString(), null, null, actual, notes);
            }
            notes.add("sha1 校验通过");
        }

        PackMetaResult result = parseLocal(path, notes);
        if (!result.ok()) {
            return new LoadVerdict(false, false, path.toString(), null, result.error(), null, notes);
        }
        notes.add("pack.mcmeta 解析完成；客户端材质不在本轨验证");
        return new LoadVerdict(true, true, path.toString(), result.meta(), null, null, notes);
    }

    private PackMetaResult parseLocal(Path path, List<String> notes) {
        if (Files.isDirectory(path)) {
            return parser.parseFile(path.resolve(PackMetaParser.FILE_NAME));
        }
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".zip") || name.endsWith(".jar")) {
            return parseZip(path, notes);
        }
        return PackMetaResult.failure(new PackMetaError(path.toString(), "$",
                PackMetaErrorKind.UNREADABLE, "资源包既不是目录也不是 zip / jar", null));
    }

    private PackMetaResult parseZip(Path archive, List<String> notes) {
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry entry = zip.getEntry(ZIP_META_ENTRY);
            if (entry == null) {
                return PackMetaResult.failure(new PackMetaError(archive + "!/" + ZIP_META_ENTRY, "$.pack",
                        PackMetaErrorKind.MISSING_META,
                        "压缩包内没有 pack.mcmeta", PackMetaParser.MOJIRA_MISSING_META));
            }
            String content;
            try (InputStream in = zip.getInputStream(entry)) {
                content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            notes.add("从压缩包读取 " + ZIP_META_ENTRY);
            return parser.parse(archive + "!/" + ZIP_META_ENTRY, content);
        } catch (IOException ex) {
            return PackMetaResult.failure(new PackMetaError(archive.toString(), "$",
                    PackMetaErrorKind.UNREADABLE, "读取资源包压缩包失败: " + ex.getMessage(), null));
        }
    }

    private static Path localPath(String url) {
        String raw = url.startsWith("file:") ? url.substring("file:".length()) : url;
        return Path.of(raw);
    }

    private static String sha1(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new UncheckedIOException("计算资源包 sha1 失败: " + file, ex);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("当前 JDK 不提供 SHA-1", ex);
        }
    }
}
