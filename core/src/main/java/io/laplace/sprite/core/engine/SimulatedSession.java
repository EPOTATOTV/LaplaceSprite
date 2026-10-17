package io.laplace.sprite.core.engine;

import io.laplace.sprite.core.event.EventStream;
import io.laplace.sprite.core.model.CheatSpec;
import io.laplace.sprite.core.model.CheatTierName;
import io.laplace.sprite.core.model.ExpectedDetection;
import io.laplace.sprite.core.model.PlayerType;
import io.laplace.sprite.core.model.ProbeSnapshot;
import io.laplace.sprite.core.model.SessionContext;

/**
 * 一次待执行模拟会话（输入层产出）。
 *
 * @param playerId      模拟玩家（SimID）
 * @param playerType    合法 / 作弊
 * @param cheatSpec     作弊样本；合法玩家为空
 * @param tier          作弊档位；合法玩家为空
 * @param scenarioId    场景剧本 id
 * @param stream        归一化事件流
 * @param probe         探针桩快照
 * @param trajectoryRef 轨迹引用（可回放）
 * @param expected      预期检出
 * @param context       判定上下文（游戏模式 / 允许飞行 / 开局时长 / 插件干预），用于误报豁免
 */
public record SimulatedSession(
        String playerId,
        PlayerType playerType,
        CheatSpec cheatSpec,
        CheatTierName tier,
        String scenarioId,
        EventStream stream,
        ProbeSnapshot probe,
        String trajectoryRef,
        ExpectedDetection expected,
        SessionContext context) {

    public SimulatedSession {
        context = context == null ? SessionContext.defaults() : context;
    }

    /** 兼容构造：未声明判定上下文时按默认（生存 / 不允许飞行 / 无插件干预）。 */
    public SimulatedSession(String playerId, PlayerType playerType, CheatSpec cheatSpec,
                            CheatTierName tier, String scenarioId, EventStream stream,
                            ProbeSnapshot probe, String trajectoryRef, ExpectedDetection expected) {
        this(playerId, playerType, cheatSpec, tier, scenarioId, stream, probe, trajectoryRef, expected,
                SessionContext.defaults());
    }

    public static SimulatedSession legit(String playerId, String scenarioId, EventStream stream,
                                         String trajectoryRef) {
        return new SimulatedSession(playerId, PlayerType.LEGIT, null, null, scenarioId, stream,
                ProbeSnapshot.clean(), trajectoryRef, ExpectedDetection.notDetected(),
                SessionContext.defaults());
    }

    public static SimulatedSession cheat(String playerId, CheatSpec spec, CheatTierName tier,
                                         String scenarioId, EventStream stream, ProbeSnapshot probe,
                                         String trajectoryRef, ExpectedDetection expected) {
        return new SimulatedSession(playerId, PlayerType.CHEAT, spec, tier, scenarioId, stream, probe,
                trajectoryRef, expected, SessionContext.defaults());
    }

    /** 带上判定上下文的副本；合法模拟里用来表达创造 / 旁观 / 开局缓冲等场景。 */
    public SimulatedSession withContext(SessionContext sessionContext) {
        return new SimulatedSession(playerId, playerType, cheatSpec, tier, scenarioId, stream, probe,
                trajectoryRef, expected, sessionContext);
    }

    public boolean isCheat() {
        return cheatSpec != null;
    }

    public boolean isBoundary() {
        return expected != null && expected.boundary();
    }
}
