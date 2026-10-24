package io.laplace.sprite.report;

/** 证据审计事件类型（v3.0 L3-04）；落盘时用枚举名的小写作为 EvidenceVault 的 kind。 */
public enum AuditType {
    DOWNLOAD,
    INSTANCE_START,
    TEST_RUN,
    ASSET_CHANGE,
    SANDBOX_CHECK
}
