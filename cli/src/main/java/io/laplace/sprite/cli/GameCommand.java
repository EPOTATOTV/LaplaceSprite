package io.laplace.sprite.cli;

import io.laplace.sprite.core.i18n.I18n;
import io.laplace.sprite.core.io.Json;
import io.laplace.sprite.game.DownloadResult;
import io.laplace.sprite.game.GameCache;
import io.laplace.sprite.game.GameDownloader;
import io.laplace.sprite.game.GameMetadataResolver;
import io.laplace.sprite.game.GameVersion;
import io.laplace.sprite.game.GameVersionMetadata;
import io.laplace.sprite.game.HashAlgorithm;
import io.laplace.sprite.game.HttpGameDownloader;
import io.laplace.sprite.game.VersionManifest;
import io.laplace.sprite.game.VersionManifestClient;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.Callable;

/**
 * {@code laplace game}：v3.0 L3-02 全版本游戏内核管理。
 *
 * <p>版本清单可以离线读缓存，也可以 {@code --refresh} 联网刷新；下载会校验 SHA-1，
 * 并把解析出的元数据写进缓存，实例创建只依赖缓存，不重复联网。</p>
 */
@Command(name = "game", description = "全版本游戏内核：版本清单、元数据、客户端/服务端下载、缓存清理",
        mixinStandardHelpOptions = true,
        subcommands = {
                GameCommand.ListCommand.class,
                GameCommand.DownloadCommand.class,
                GameCommand.MetadataCommand.class,
                GameCommand.CacheCleanCommand.class
        })
final class GameCommand implements Runnable {

    static final String DEFAULT_CACHE = "build/laplace/game";

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    @Command(name = "list", description = "列出前 20 个版本与总数；--refresh 联网刷新清单",
            mixinStandardHelpOptions = true)
    static final class ListCommand implements Callable<Integer> {

        @Option(names = "--refresh", description = "联网刷新版本清单并写入缓存")
        boolean refresh;

        @Option(names = "--cache", defaultValue = DEFAULT_CACHE, description = "游戏缓存目录")
        Path cache;

        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            I18n i18n = I18n.get();
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            Path cacheFile = cache.resolve("version_manifest_v2.json");
            try {
                VersionManifestClient client = new VersionManifestClient(cacheFile);
                Optional<VersionManifest> loaded = refresh
                        ? Optional.of(client.fetch()) : client.loadCached();
                if (loaded.isEmpty()) {
                    err.println(i18n.t("game.list.missing"));
                    err.println("  " + i18n.t("game.list.cache", cacheFile.toAbsolutePath()));
                    return 1;
                }
                VersionManifest manifest = loaded.get();
                out.println(i18n.t("game.list.header", manifest.latestRelease(),
                        manifest.latestSnapshot(), manifest.count()));
                manifest.versions().stream().limit(20).forEach(version ->
                        out.printf("  %-24s %-10s %s%n", version.id(), version.type(),
                                version.releaseTime()));
                return 0;
            } catch (IOException | IllegalArgumentException ex) {
                err.println(i18n.t("game.list.read.failed", ex.getMessage()));
                return 1;
            }
        }
    }

    @Command(name = "download", description = "下载客户端/服务端并校验 SHA-1，输出元数据",
            mixinStandardHelpOptions = true)
    static final class DownloadCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<version>", description = "版本 id，如 26.3")
        String versionId;

        @Option(names = "--client", description = "下载 client.jar（未指定 --server 时默认下载客户端）")
        boolean client;

        @Option(names = "--server", description = "下载 server.jar")
        boolean server;

        @Option(names = "--cache", defaultValue = DEFAULT_CACHE, description = "游戏缓存目录")
        Path cache;

        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            I18n i18n = I18n.get();
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            GameCache gameCache = new GameCache(cache);
            try {
                VersionManifest manifest = loadManifest(gameCache);
                Optional<GameVersion> selected = manifest.find(versionId);
                if (selected.isEmpty()) {
                    err.println(i18n.t("game.download.version.missing", versionId));
                    return 1;
                }
                GameVersion version = selected.get();
                String versionJson = VersionManifestClient.httpFetcher()
                        .fetch(URI.create(version.manifestUrl()));
                Files.createDirectories(gameCache.versionDir(version.id()));
                Files.writeString(gameCache.versionFile(version.id()), versionJson,
                        StandardCharsets.UTF_8);
                GameVersionMetadata metadata = new GameMetadataResolver()
                        .resolve(version.id(), versionJson);
                writeMetadata(gameCache, metadata);
                printMetadata(out, metadata);

                boolean wantClient = client || !server;
                GameDownloader downloader = new HttpGameDownloader();
                boolean ok = true;
                if (wantClient) {
                    ok &= download(out, err, downloader, metadata.client(),
                            gameCache.clientJar(version.id()), "client.jar");
                }
                if (server) {
                    ok &= download(out, err, downloader, metadata.server(),
                            gameCache.serverJar(version.id()), "server.jar");
                }
                return ok ? 0 : 1;
            } catch (IOException | IllegalArgumentException ex) {
                err.println(i18n.t("game.download.failed", ex.getMessage()));
                return 1;
            }
        }

        private static VersionManifest loadManifest(GameCache cache) throws IOException {
            VersionManifestClient client = new VersionManifestClient(
                    cache.root().resolve("version_manifest_v2.json"));
            Optional<VersionManifest> cached = client.loadCached();
            return cached.isPresent() ? cached.get() : client.fetch();
        }

        private static boolean download(PrintWriter out, PrintWriter err, GameDownloader downloader,
                                        GameVersionMetadata.DownloadInfo info,
                                        Path target, String name) throws IOException {
            I18n i18n = I18n.get();
            if (info == null) {
                err.println(i18n.t("game.download.artifact.missing", name));
                return false;
            }
            DownloadResult result = downloader.download(URI.create(info.url()), target,
                    info.sha1(), HashAlgorithm.SHA1);
            out.println("  " + i18n.t("game.download.ok", name, result.bytes(), result.attempts(),
                    result.sha1(), result.sha256()));
            out.println("  " + i18n.t("game.download.cache", target.toAbsolutePath()));
            return result.verified();
        }
    }

    @Command(name = "metadata", description = "只解析已缓存的 version.json，不联网",
            mixinStandardHelpOptions = true)
    static final class MetadataCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<version>", description = "版本 id")
        String versionId;

        @Option(names = "--cache", defaultValue = DEFAULT_CACHE, description = "游戏缓存目录")
        Path cache;

        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            I18n i18n = I18n.get();
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            GameCache gameCache = new GameCache(cache);
            try {
                Path versionFile = gameCache.versionFile(versionId);
                if (!Files.isRegularFile(versionFile)) {
                    err.println(i18n.t("game.metadata.missing", versionFile.toAbsolutePath()));
                    err.println("  " + i18n.t("game.metadata.hint", versionId));
                    return 1;
                }
                String versionJson = Files.readString(versionFile, StandardCharsets.UTF_8);
                GameVersionMetadata metadata = new GameMetadataResolver()
                        .resolve(versionId, versionJson);
                printMetadata(out, metadata);
                return 0;
            } catch (IOException | IllegalArgumentException ex) {
                err.println(i18n.t("game.metadata.failed", ex.getMessage()));
                return 1;
            }
        }
    }

    @Command(name = "cache-clean", description = "清理单个版本或全部版本的缓存",
            mixinStandardHelpOptions = true)
    static final class CacheCleanCommand implements Callable<Integer> {

        @Option(names = "--version", description = "只清理该版本；不给则清理全部")
        String versionId;

        @Option(names = "--cache", defaultValue = DEFAULT_CACHE, description = "游戏缓存目录")
        Path cache;

        @Spec
        CommandSpec spec;

        @Override
        public Integer call() {
            I18n i18n = I18n.get();
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            GameCache gameCache = new GameCache(cache);
            try {
                if (versionId == null) {
                    gameCache.cleanAll();
                    out.println(i18n.t("game.cache.clean.all.ok",
                            gameCache.versionsDir().toAbsolutePath()));
                } else {
                    gameCache.clean(versionId);
                    out.println(i18n.t("game.cache.clean.ok",
                            gameCache.versionDir(versionId).toAbsolutePath()));
                }
                return 0;
            } catch (IOException | IllegalArgumentException ex) {
                err.println(i18n.t("game.cache.clean.failed", ex.getMessage()));
                return 1;
            }
        }
    }

    private static void printMetadata(PrintWriter out, GameVersionMetadata metadata) {
        I18n i18n = I18n.get();
        out.println(i18n.t("game.metadata.header", metadata.id(), metadata.type(),
                metadata.releaseTime()));
        out.println("  " + i18n.t("game.metadata.detail",
                metadata.mainClass() == null ? i18n.t("game.metadata.unknown") : metadata.mainClass(),
                metadata.assets() == null ? i18n.t("game.metadata.unknown") : metadata.assets()));
        out.println("  " + i18n.t("game.metadata.version", number(metadata.packFormat()),
                number(metadata.dataVersion()), number(metadata.protocolVersion())));
        out.println("  " + i18n.t("game.metadata.render", metadata.defaultRenderApi(),
                metadata.mappings()));
    }

    private static String number(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : I18n.get().t("game.metadata.unknown");
    }

    private static void writeMetadata(GameCache cache, GameVersionMetadata metadata) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", metadata.id());
        root.put("type", metadata.type());
        root.put("releaseTime", metadata.releaseTime());
        root.put("mainClass", metadata.mainClass());
        root.put("assets", metadata.assets());
        metadata.packFormat().ifPresent(value -> root.put("packFormat", value));
        metadata.dataVersion().ifPresent(value -> root.put("dataVersion", value));
        metadata.protocolVersion().ifPresent(value -> root.put("protocolVersion", value));
        root.put("defaultRenderApi", metadata.defaultRenderApi());
        root.put("mappings", metadata.mappings());
        root.put("client", downloadInfo(metadata.client()));
        root.put("server", downloadInfo(metadata.server()));
        try {
            Json.writeFile(cache.metadataFile(metadata.id()), root);
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private static Map<String, Object> downloadInfo(GameVersionMetadata.DownloadInfo info) {
        if (info == null) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("url", info.url());
        values.put("sha1", info.sha1());
        values.put("sha256", info.sha256());
        values.put("size", info.size());
        return values;
    }
}
