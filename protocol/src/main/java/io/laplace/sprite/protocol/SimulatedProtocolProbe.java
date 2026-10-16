package io.laplace.sprite.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * 离线协议探测（15.6）：按固定兼容策略模拟 Via 的行为，供 CI 与单元测试使用。
 *
 * <p>策略是模拟出来的模型，不是对真实 Via 行为的断言：</p>
 * <ul>
 *   <li>基岩格走 Geyser + Floodgate，五阶段全通过。</li>
 *   <li>客户端与服务端版本一致：全通过，不需要协议转换。</li>
 *   <li>版本不一致且没走 ViaProxy：LOGIN 阶段失败（后端没装 Via 三件套）。</li>
 *   <li>版本不一致且走 ViaProxy：次版本跨度小于 {@link #LARGE_GAP_MINOR} 时全通过；
 *       跨度达到阈值时 PLACE_BLOCK 失败（模拟块状态 ID 映射缺口）。</li>
 * </ul>
 */
public final class SimulatedProtocolProbe implements ProtocolProbe {

    /** 次版本跨度达到该值即模拟为「映射缺口」，即使有 Via 也会掉在放置方块阶段。 */
    public static final int LARGE_GAP_MINOR = 3;

    @Override
    public List<ProtocolStageResult> run(ProtocolMatrixCell cell) {
        if (cell.bedrock()) {
            return stagesUpTo(null, "Geyser + Floodgate 基岩登录链路通过");
        }
        if (cell.clientVersion().equals(cell.serverVersion())) {
            return stagesUpTo(null, "版本一致，无需协议转换");
        }
        if (!cell.viaProxy()) {
            return stagesUpTo(ProtocolStage.LOGIN,
                    "后端未接 ViaProxy，客户端 %s 与服务端 %s 版本不一致，登录阶段失败"
                            .formatted(cell.clientVersion(), cell.serverVersion()));
        }
        if (minorDistance(cell.clientVersion(), cell.serverVersion()) >= LARGE_GAP_MINOR) {
            return stagesUpTo(ProtocolStage.PLACE_BLOCK,
                    "次版本跨度 %d，超出块状态映射覆盖范围，放置方块阶段失败"
                            .formatted(minorDistance(cell.clientVersion(), cell.serverVersion())));
        }
        return stagesUpTo(null, "Via 三件套兜底，最小闭环通过");
    }

    private static List<ProtocolStageResult> stagesUpTo(ProtocolStage failedStage, String detail) {
        List<ProtocolStageResult> results = new ArrayList<>();
        for (ProtocolStage stage : ProtocolStage.ordered()) {
            if (stage == failedStage) {
                results.add(ProtocolStageResult.fail(stage, detail));
                break;
            }
            results.add(ProtocolStageResult.pass(stage, "通过"));
        }
        return results;
    }

    /**
     * 次版本号距离：按版本串第 2 段数字估算，解析不出来按 1 处理。
     */
    static int minorDistance(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        if (a.length < 2 || b.length < 2) {
            return 1;
        }
        try {
            return Math.abs(Integer.parseInt(digits(a[1])) - Integer.parseInt(digits(b[1])));
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private static String digits(String token) {
        return token.replaceAll("[^0-9].*$", "");
    }
}
