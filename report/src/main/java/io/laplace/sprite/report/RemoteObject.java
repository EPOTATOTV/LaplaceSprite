package io.laplace.sprite.report;

import java.time.Instant;

/**
 * 远端对象（4.6）：一次成功上传在远端的落点记录。
 *
 * @param remoteRef  远端引用，{@link EvidenceRemoteStore#get} 与 {@link EvidenceRemoteStore#exists} 的键
 * @param evidenceId 对应的本地证据 id
 * @param size       字节数
 * @param sha256     内容的 SHA-256（hex），与证据链 payloadHash 同算法，可直接比对
 * @param storedAt   上传时间
 */
public record RemoteObject(String remoteRef, String evidenceId, long size, String sha256, Instant storedAt) {
}
