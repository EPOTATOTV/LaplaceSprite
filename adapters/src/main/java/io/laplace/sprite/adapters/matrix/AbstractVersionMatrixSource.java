package io.laplace.sprite.adapters.matrix;

import io.laplace.sprite.adapters.support.HttpFetcher;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.VersionMatrixEntry;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 版本矩阵来源骨架（14.7）：一次 HTTP 尝试 + 本地快照兜底 + pin 持久化。
 *
 * <p>{@link #fetch} 的语义是「尽力而为」：端点可达且解析出条目就用网络结果，
 * 否则原样返回本地快照，绝不因此抛异常。</p>
 */
public abstract class AbstractVersionMatrixSource implements VersionMatrixSource {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final HttpFetcher http;
    private final PinStore pins;

    protected AbstractVersionMatrixSource(boolean networkEnabled, Path pinFile) {
        this.http = new HttpFetcher(DEFAULT_TIMEOUT, networkEnabled);
        this.pins = new PinStore(pinFile);
    }

    @Override
    public List<VersionMatrixEntry> fetch(PlatformFamily family) {
        if (family != family()) {
            return List.of();
        }
        Optional<String> body = http.get(endpoint());
        if (body.isPresent()) {
            List<VersionMatrixEntry> parsed = parsePayload(body.get());
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }
        return snapshot(family);
    }

    @Override
    public List<VersionMatrixEntry> snapshot(PlatformFamily family) {
        if (family != family()) {
            return List.of();
        }
        return MatrixSnapshots.load(snapshotFile(), family, sourceName());
    }

    @Override
    public void pin(VersionMatrixEntry entry) {
        pins.add(entry);
    }

    public List<VersionMatrixEntry> pinned() {
        return pins.entries();
    }

    public Path pinFile() {
        return pins.file();
    }

    /** 运行时端点。 */
    protected abstract URI endpoint();

    /** 解析运行时响应；认不出的响应返回空列表，由调用方退化到快照。 */
    protected abstract List<VersionMatrixEntry> parsePayload(String body);

    /** 离线快照文件名（位于 version-matrix/ 下）。 */
    protected abstract String snapshotFile();

    /** 来源标识（写进 {@link VersionMatrixEntry#source()}）。 */
    protected abstract String sourceName();
}
