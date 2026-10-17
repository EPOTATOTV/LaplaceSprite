package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.model.FlagEvent;

import java.util.List;

/**
 * 检测目标（4.4）：检测引擎进程内直连、客户端扫描器探针桩、各平台插件端到端。
 *
 * <p>实现由适配器提供（SimServerAdapter / 平台适配器），Runner 只面向本接口。</p>
 */
public interface DetectionTarget {

    String id();

    /** 把事件流灌入检测引擎，返回其产出的 flag。 */
    List<FlagEvent> detect(DetectionContext ctx);
}
