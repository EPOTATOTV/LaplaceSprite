package io.laplace.sprite.adapters.proxy;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** BungeeCord（14.2）：代理端基线，代理事件按意图完成顺序补进归一化流。 */
public final class BungeeCordAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "BungeeCord",
            "构建 plugins/ 目录，bungee.yml 由代理端装载器读取",
            "bungee.yml",
            "BungeeCord 单线程事件循环（代理端无 tick 概念）",
            AdapterStatus.IMPLEMENTED,
            List.of("registerIntent 完成后才产出 ProxyPlayerJoin / ProxyChat / ProxyPlayerLeave",
                    "代理事件顺序固定为 join → chat → leave，供断言按序核对",
                    "后端服务器切换不产生 PLAYER_LEAVE，只更新目标服务器字段"));

    public BungeeCordAdapter() {
        super("bungeecord", PlatformFamily.BUNGEECORD, SupportRange.any(), FACTS);
    }
}
