package io.laplace.sprite.report;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地镜像远端库（4.6）：把「云存储」落成第二个本地目录，让上传 / 校验整条路径离线可跑。
 *
 * <p>默认给测试与 CI 用；真实云端见 {@link HttpRemoteStore}。远端引用即镜像目录下的相对路径。</p>
 */
public final class LocalMirrorRemoteStore implements EvidenceRemoteStore {

    private final Path mirrorRoot;
    private final Map<String, RemoteObject> objects = new ConcurrentHashMap<>();

    public LocalMirrorRemoteStore(Path mirrorRoot) {
        this.mirrorRoot = mirrorRoot;
    }

    public Path mirrorRoot() {
        return mirrorRoot;
    }

    @Override
    public String put(String evidenceId, String relativePath, byte[] payload) {
        String ref = EvidenceRemoteStore.normalizeKey(relativePath);
        Path target = mirrorRoot.resolve(ref);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, payload);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入本地镜像失败: " + target, ex);
        }
        objects.put(ref, new RemoteObject(ref, evidenceId, payload.length,
                EvidenceChainLink.payloadHash(payload), Instant.now()));
        return ref;
    }

    @Override
    public byte[] get(String remoteRef) {
        try {
            return Files.readAllBytes(mirrorRoot.resolve(EvidenceRemoteStore.normalizeKey(remoteRef)));
        } catch (IOException ex) {
            throw new UncheckedIOException("读取本地镜像失败: " + remoteRef, ex);
        }
    }

    @Override
    public boolean exists(String remoteRef) {
        return Files.isRegularFile(mirrorRoot.resolve(EvidenceRemoteStore.normalizeKey(remoteRef)));
    }

    /** 本会话内上传过的对象，按引用排序。 */
    @Override
    public List<RemoteObject> list() {
        return objects.values().stream()
                .sorted(Comparator.comparing(RemoteObject::remoteRef))
                .toList();
    }

    @Override
    public String describe() {
        return "本地镜像远端库（目录 %s）：上传即写第二份，供离线校验".formatted(mirrorRoot);
    }
}
