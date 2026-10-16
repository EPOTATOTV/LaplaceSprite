package io.laplace.sprite.protocol.adapter;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/**
 * ViaProxy 协议代理适配器（15.6 归属）：独立「协议代理适配器」，落在代理端家族。
 *
 * <p>只是把协议兼容轨暴露成普通适配器：装配、事件桥、断言契约都复用代理端家族那一套，
 * 协议转换本身由 ViaProxy 进程负责，不在适配器里实现。</p>
 */
public final class ViaProxyAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "ViaProxy",
            "独立代理进程承接协议转换，后端服务端无需逐个装 Via 三件套",
            "viaproxy.yml",
            "ViaProxy 自有事件循环（代理端无 tick 概念）",
            AdapterStatus.IMPLEMENTED,
            List.of("协议格先查能力再跑闭环：版本一致直接过，跨版本由 Via 兜底",
                    "基岩格挂 Geyser + Floodgate，认证走 floodgate，不依赖正版验证",
                    "数据包断言需 Paper 侧 ProtocolLib 监听器录制序列，离线只用录制夹具"));

    public ViaProxyAdapter() {
        super("viaproxy", PlatformFamily.VELOCITY, SupportRange.any(), FACTS);
    }
}
