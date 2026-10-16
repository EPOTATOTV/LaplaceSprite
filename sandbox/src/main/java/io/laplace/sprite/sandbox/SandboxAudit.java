package io.laplace.sprite.sandbox;

/**
 * 敏感路径审计条目（15.4）。
 *
 * @param path      被审计的宿主路径
 * @param sensitive 是否命中敏感路径
 * @param reason    命中原因；未命中为空串
 */
public record SandboxAudit(String path, boolean sensitive, String reason) {

    public SandboxAudit {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("被审计路径不能为空");
        }
        reason = reason == null ? "" : reason;
    }

    public static SandboxAudit clean(String path) {
        return new SandboxAudit(path, false, "");
    }

    public static SandboxAudit sensitive(String path, String reason) {
        return new SandboxAudit(path, true, reason);
    }
}
