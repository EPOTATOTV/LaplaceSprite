package io.laplace.sprite.adapters;

import java.util.List;
import java.util.Objects;

/**
 * 平台事实表（14.2 服务器插件接入矩阵 / 14.3 模组加载器接入矩阵一行）。
 *
 * <p>不写进代码逻辑的东西一律放这里：装配说明、描述符文件名、调度模型、接入状态与测试适配要点，
 * 都是文档表格里的数据，便于报告直接引用。</p>
 *
 * @param platformName      平台名
 * @param assemblyDescription 装配说明（产物落到哪里、启动方式）
 * @param descriptorFile    平台描述符文件名
 * @param schedulerModel    调度模型
 * @param status            接入状态
 * @param testAdaptationNotes 测试适配要点
 */
public record PlatformFacts(String platformName, String assemblyDescription, String descriptorFile,
                            String schedulerModel, AdapterStatus status,
                            List<String> testAdaptationNotes) {

    public PlatformFacts {
        Objects.requireNonNull(platformName, "platformName");
        Objects.requireNonNull(status, "status");
        testAdaptationNotes = testAdaptationNotes == null ? List.of() : List.copyOf(testAdaptationNotes);
    }
}
