package io.laplace.sprite.adapters.artifact;

import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactSource;
import io.laplace.sprite.core.model.ArtifactSpec;
import io.laplace.sprite.core.spi.ArtifactResolver;

import java.util.List;

/**
 * 按来源选择产物解析器（14.4 ① / 14.5）。
 *
 * <p>内核只拿到一个 {@link ArtifactResolver}，来源分派在适配器层完成；四个客户端各持有自己的
 * base URL 与离线快照策略，默认实例是全局共享的。</p>
 */
public final class ArtifactResolverFactory {

    private static final MavenArtifactResolver MAVEN = new MavenArtifactResolver();
    private static final ModrinthClient MODRINTH = new ModrinthClient();
    private static final CurseForgeClient CURSEFORGE = new CurseForgeClient();
    private static final PackwizResolver PACKWIZ = new PackwizResolver();
    private static final LocalArtifactResolver LOCAL = new LocalArtifactResolver();
    private static final ArtifactResolver DISPATCHER = new Dispatcher();

    private ArtifactResolverFactory() {
    }

    /** 默认解析器：按 {@link ArtifactSpec#source()} 分派。 */
    public static ArtifactResolver defaultResolver() {
        return DISPATCHER;
    }

    public static ArtifactResolver forSource(ArtifactSource source) {
        return switch (source) {
            case MAVEN -> MAVEN;
            case MODRINTH -> MODRINTH;
            case CURSEFORGE -> CURSEFORGE;
            case PACKWIZ -> PACKWIZ;
            case BUILT, LOCAL -> LOCAL;
        };
    }

    private static final class Dispatcher implements ArtifactResolver {

        @Override
        public Artifact resolve(ArtifactSpec spec) {
            return forSource(spec.source()).resolve(spec);
        }

        @Override
        public List<Artifact> resolveDependencies(Artifact artifact) {
            return forSource(artifact.source()).resolveDependencies(artifact);
        }
    }
}
