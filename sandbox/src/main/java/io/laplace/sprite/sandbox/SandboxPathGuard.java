package io.laplace.sprite.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 沙箱路径闸门（v3.0 L3-04）：容器内外任何读写都要先确认目标落在显式实例根内。
 *
 * <p>允许根本身是通过的，但敏感路径即使位于允许根之下也一律拒绝：把整个 {@code C:\} 设成允许根
 * 不能顺便把系统目录开放出来。符号链接经 {@code toRealPath} 解析，指向根外时包含性检查自然失败；
 * 目标尚不存在时先把最近的已存在祖先解析成真实路径，再拼回剩余段，避免父目录是链接时逃逸。</p>
 */
public final class SandboxPathGuard {

    private final SandboxAuditor auditor;

    public SandboxPathGuard() {
        this(new SandboxAuditor());
    }

    public SandboxPathGuard(SandboxAuditor auditor) {
        this.auditor = auditor;
    }

    /** 校验读取目标；不通过抛 {@link SecurityException}，通过返回规范化后的真实路径。 */
    public Path checkRead(Path target, List<Path> allowedRoots) {
        return check(target, allowedRoots, "读取");
    }

    /** 校验写入目标；不通过抛 {@link SecurityException}，通过返回规范化后的真实路径。 */
    public Path checkWrite(Path target, Path writableRoot) {
        return check(target, List.of(writableRoot), "写入");
    }

    private Path check(Path target, List<Path> allowedRoots, String action) {
        if (target == null) {
            throw new IllegalArgumentException("被" + action + "的路径不能为空");
        }
        if (allowedRoots == null || allowedRoots.isEmpty()) {
            throw new SecurityException("未配置允许根目录，拒绝" + action + " " + target);
        }

        Path resolved = resolve(target);
        SandboxAudit audit = auditor.auditPath(resolved.toString());
        if (audit.sensitive()) {
            throw new SecurityException("拒绝" + action + "敏感路径 " + resolved + "（" + audit.reason() + "）");
        }

        boolean inside = allowedRoots.stream()
                .filter(root -> root != null)
                .anyMatch(root -> resolved.startsWith(resolve(root)));
        if (!inside) {
            throw new SecurityException("拒绝" + action + "允许根之外的路径 " + resolved);
        }
        return resolved;
    }

    /** 已存在就取真实路径（跟随符号链接），否则解析最近祖先再拼回剩余段。 */
    private static Path resolve(Path target) {
        Path absolute = target.toAbsolutePath();
        try {
            if (Files.exists(absolute)) {
                return absolute.toRealPath();
            }
            Path existing = absolute.getParent();
            Path remainder = absolute.getFileName();
            while (existing != null && !Files.exists(existing)) {
                Path name = existing.getFileName();
                if (name != null) {
                    remainder = remainder == null ? name : name.resolve(remainder);
                }
                existing = existing.getParent();
            }
            if (existing == null) {
                return absolute.normalize();
            }
            Path real = existing.toRealPath();
            return remainder == null ? real : real.resolve(remainder).normalize();
        } catch (IOException ex) {
            throw new SecurityException("无法解析路径 " + target, ex);
        }
    }
}
