package io.laplace.sprite.game;

import java.time.Instant;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * 由官方版本 JSON 解析出的运行元数据。
 *
 * @param id                版本 id
 * @param type              版本类型
 * @param releaseTime       发布时间
 * @param mainClass         主类；旧版本可能没有
 * @param assets            assets 索引 id
 * @param packFormat        数据包格式；不在对照表内时为空
 * @param dataVersion       存档 DataVersion；只有核实过的版本给值
 * @param protocolVersion   网络协议号；只有核实过的版本给值
 * @param defaultRenderApi  默认渲染后端
 * @param mappings          默认映射集
 * @param client            客户端下载项；缺失时为 null
 * @param server            服务端下载项；旧版本可能为 null
 */
public record GameVersionMetadata(String id, String type, Instant releaseTime, String mainClass,
                                  String assets, OptionalInt packFormat, OptionalInt dataVersion,
                                  OptionalInt protocolVersion, RenderApi defaultRenderApi,
                                  MappingSet mappings, DownloadInfo client, DownloadInfo server) {

    public GameVersionMetadata {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(releaseTime, "releaseTime");
        Objects.requireNonNull(packFormat, "packFormat");
        Objects.requireNonNull(dataVersion, "dataVersion");
        Objects.requireNonNull(protocolVersion, "protocolVersion");
        Objects.requireNonNull(defaultRenderApi, "defaultRenderApi");
        Objects.requireNonNull(mappings, "mappings");
    }

    /**
     * 一个可下载产物。
     *
     * @param url    下载地址
     * @param sha1   SHA-1，官方版本 JSON 必给
     * @param sha256 SHA-256；官方版本 JSON 通常没有，此时为 null
     * @param size   字节数
     */
    public record DownloadInfo(String url, String sha1, String sha256, long size) {

        public DownloadInfo {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(sha1, "sha1");
            if (size < 0) {
                throw new IllegalArgumentException("下载大小不能为负: " + size);
            }
        }
    }
}
