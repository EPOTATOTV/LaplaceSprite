package io.laplace.sprite.core.model;

/**
 * 场景剧本运行进度（11.3 场景剧本进度）。
 */
public record ScenarioRun(String scenarioId, String name, double progressPercent, int injectedSamples,
                          RunStatus status, PlatformKey platform) {

    public ScenarioRun withProgress(double percent) {
        return new ScenarioRun(scenarioId, name, percent, injectedSamples, status, platform);
    }
}
