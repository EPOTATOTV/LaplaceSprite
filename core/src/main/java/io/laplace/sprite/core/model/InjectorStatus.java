package io.laplace.sprite.core.model;

/**
 * 注入器状态（11.5 注入器状态）。
 *
 * @param cheatInjectionEnabled 作弊注入开关
 * @param probeStubActive       ProbeStub 是否激活
 * @param probeSnapshotRef      探针快照引用
 * @param reportedSeq           事件流上报 seq
 * @param evidenceRecording     证据录制
 */
public record InjectorStatus(boolean cheatInjectionEnabled, boolean probeStubActive,
                             String probeSnapshotRef, long reportedSeq, boolean evidenceRecording) {

    public static InjectorStatus idle() {
        return new InjectorStatus(false, false, null, 0L, false);
    }
}
