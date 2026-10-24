package io.laplace.sprite.cli;

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
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            Path cacheFile = cache.resolve("version_manifest_v2.json");
            try {
                VersionManifestClient client = new VersionManifestClient(cacheFile);
                Optional<VersionManifest> loaded = refresh
                        ? Optional.of(client.fetch()) : client.loadCached();
                if (loaded.isEmpty()) {
                    err.println("版本清单缓存不存在，先执行 laplace game list --refresh");
                    err.println("  缓存路径 " + cacheFile.toAbsolutePath());
                    return 1;
                }
                VersionManifest manifest = loaded.get();
                out.printf("版本清单：latest release %s · latest snapshot %s · 共 %d 个%n",
                        manifest.latestRelease(), manifest.latestSnapshot(), manifest.count());
                manifest.versions().stream().limit(20).forEach(version ->
                        out.printf("  %-24s %-10s %s%n", version.id(), version.type(),
                                version.releaseTime()));
                return 0;
            } catch (IOException | IllegalArgumentException ex) {
                err.println("版本清单读取失败: " + ex.getMessage());
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
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            GameCache gameCache = new GameCache(cache);
            try {
                VersionManifest manifest = loadManifest(gameCache);
                Optional<GameVersion> selected = manifest.find(versionId);
                if (selected.isEmpty()) {
                    err.println("版本清单里没有这个版本: " + versionId);
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
                err.println("下载失败: " + ex.getMessage());
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
            if (info == null) {
                err.println("该版本没有 " + name + " 下载项");
                return false;
            }
            DownloadResult result = downloader.download(URI.create(info.url()), target,
                    info.sha1(), HashAlgorithm.SHA1);
            out.printf("  %s %d 字节 · 尝试 %d 次 · SHA-1 %s · SHA-256 %s%n", name, result.bytes(),
                    result.attempts(), result.sha1(), result.sha256());
            out.println("  缓存 " + target.toAbsolutePath());
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
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            GameCache gameCache = new GameCache(cache);
            try {
                Path versionFile = gameCache.versionFile(versionId);
                if (!Files.isRegularFile(versionFile)) {
                    err.println("没有缓存的 version.json: " + versionFile.toAbsolutePath());
                    err.println("  先执行 laplace game download " + versionId);
                    return 1;
                }
                String versionJson = Files.readString(versionFile, StandardCharsets.UTF_8);
                GameVersionMetadata metadata = new GameMetadataResolver()
                        .resolve(versionId, versionJson);
                printMetadata(out, metadata);
                return 0;
            } catch (IOException | IllegalArgumentException ex) {
                err.println("元数据解析失败: " + ex.getMessage());
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
            PrintWriter out = spec.commandLine().getOut();
            PrintWriter err = spec.commandLine().getErr();
            GameCache gameCache = new GameCache(cache);
            try {
                if (versionId == null) {
                    gameCache.cleanAll();
                    out.println("已清理全部版本缓存: " + gameCache.versionsDir().toAbsolutePath());
                } else {
                    gameCache.clean(versionId);
                    out.println("已清理版本缓存: " + gameCache.versionDir(versionId).toAbsolutePath());
                }
                return 0;
            } catch (IOException | IllegalArgumentException ex) {
                err.println("缓存清理失败: " + ex.getMessage());
                return 1;
            }
        }
    }

    private static void printMetadata(PrintWriter out, GameVersionMetadata metadata) {
        out.printf("元数据 %s · type %s · 发布时间 %s%n", metadata.id(), metadata.type(),
                metadata.releaseTime());
        out.printf("  mainClass %s · assets %s%n",
                metadata.mainClass() == null ? "未知" : metadata.mainClass(),
                metadata.assets() == null ? "未知" : metadata.assets());
        out.printf("  pack_format %s · dataVersion %s · protocolVersion %s%n",
                number(metadata.packFormat()), number(metadata.dataVersion()),
                number(metadata.protocolVersion()));
        out.printf("  渲染 API %s · 映射 %s%n", metadata.defaultRenderApi(), metadata.mappings());
    }

    private static String number(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : "未知";
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
