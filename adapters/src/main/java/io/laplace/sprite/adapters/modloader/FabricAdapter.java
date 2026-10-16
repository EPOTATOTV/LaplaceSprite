package io.laplace.sprite.adapters.modloader;

import io.laplace.sprite.adapters.AbstractLaplaceAdapter;
import io.laplace.sprite.adapters.AdapterStatus;
import io.laplace.sprite.adapters.PlatformFacts;
import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.spi.SupportRange;

import java.util.List;

/** Fabric（14.3）：一级接入目标，fabric.mod.json 的五个依赖层级要分别映射。 */
public final class FabricAdapter extends AbstractLaplaceAdapter {

    private static final PlatformFacts FACTS = new PlatformFacts(
            "Fabric",
            "构建 mods/ 目录，模组元数据在 fabric.mod.json（schemaVersion 1）",
            "fabric.mod.json",
            "Fabric 主线程 tick + mixin 注入",
            AdapterStatus.IMPLEMENTED_TIER1,
            List.of("五级依赖语义：depends→硬，recommends/suggests→软，breaks/conflicts→反向",
                    "environment 决定模组只在 client / server / 两侧生效，服务端测试只取 server|*",
                    "mixin 注入会改变原版方法行为，行为断言需与该平台的合法基线一起冻结"));

    private static final SupportRange RANGE = new SupportRange(null, null, "0.14.0", null);

    public FabricAdapter() {
        super("fabric", PlatformFamily.FABRIC, RANGE, FACTS);
    }
}
