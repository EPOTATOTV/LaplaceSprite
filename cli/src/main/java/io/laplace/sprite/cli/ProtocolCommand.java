package io.laplace.sprite.cli;

import io.laplace.sprite.protocol.FieldMatcher;
import io.laplace.sprite.protocol.GeyserBridgeSpec;
import io.laplace.sprite.protocol.PacketAssertionEngine;
import io.laplace.sprite.protocol.PacketAssertionSpec;
import io.laplace.sprite.protocol.ProtocolMatrix;
import io.laplace.sprite.protocol.ProtocolMatrixCell;
import io.laplace.sprite.protocol.RecordedPacket;
import io.laplace.sprite.protocol.RecordedPacketSequence;
import io.laplace.sprite.protocol.SimulatedProtocolProbe;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * {@code laplace protocol}：15.6 协议兼容轨。
 *
 * <p>矩阵与数据包断言都在离线替身上跑：真实格需要 ViaProxy / Geyser / ProtocolLib，
 * 本命令只报告替身结论并明确标注证据来源。</p>
 */
@Command(name = "protocol",
        description = "协议兼容轨：服务端 × 客户端版本矩阵、ViaProxy 入口、数据包级断言",
        mixinStandardHelpOptions = true)
final class ProtocolCommand implements Callable<Integer> {

    /** 默认的候选客户端版本：矩阵按相邻 3-5 个版本收敛。 */
    private static final List<String> CANDIDATE_CLIENTS = List.of(
            "1.20.1", "1.20.4", "1.20.6", "1.21", "1.21.1", "1.21.4", "1.21.11");

    @Option(names = "--server-version", defaultValue = "1.21.11", description = "被测服务端版本")
    String serverVersion;

    @Option(names = "--client", description = "候选客户端版本，可重复；缺省用内置相邻版本集")
    List<String> clients;

    @Option(names = "--no-via-proxy", description = "不经 ViaProxy 统一入口（用于对比失败格）")
    boolean noViaProxy;

    @Option(names = "--bedrock", description = "追加基岩格（Geyser + Floodgate）")
    boolean bedrock;

    @Spec
    CommandSpec spec;

    @Override
    public Integer call() {
        var writer = spec.commandLine().getOut();
        writer.println("协议兼容轨（15.6） · 服务端 " + serverVersion
                + " · ViaProxy 入口 " + !noViaProxy);

        ProtocolMatrix matrix = new ProtocolMatrix(new SimulatedProtocolProbe())
                .withViaProxy(!noViaProxy);
        List<ProtocolMatrixCell> cells = matrix.run(serverVersion,
                clients == null || clients.isEmpty() ? CANDIDATE_CLIENTS : clients);

        writer.println("  矩阵单元 " + cells.size() + " 格（最小闭环：登录 → 进世界 → 放置方块 → 打怪 → 聊天）");
        cells.forEach(cell -> writer.printf("    %s 客户端 %-8s 通过=%s%s%n", cell.serverVersion(),
                cell.clientVersion(), cell.ok(),
                cell.ok() ? "" : " 失败阶段=" + cell.failedStage()));

        if (bedrock) {
            ProtocolMatrixCell bedrockCell = matrix.runBedrock(serverVersion);
            writer.printf("    基岩格 通过=%s%s%n", bedrockCell.ok(),
                    bedrockCell.ok() ? "" : " 失败阶段=" + bedrockCell.failedStage());
            GeyserBridgeSpec geyser = GeyserBridgeSpec.standard(null, null, 25565);
            writer.println("    Geyser 可用=" + geyser.available() + " 安装目标=" + geyser.installTargets());
            geyser.notes().forEach(note -> writer.println("      · " + note));
        }

        writer.println("  失败格 " + matrix.failedCells().size() + " 个");
        matrix.failureSummary().forEach(line -> writer.println("    · " + line));

        writer.println("  数据包级断言（离线夹具，真实抓包需 Paper 侧 ProtocolLib 监听器）");
        List<RecordedPacket> sequence = RecordedPacketSequence.loadDefault();
        var evaluation = new PacketAssertionEngine().evaluate(sequence, assertionSpecs());
        writer.println("    夹具包数 " + sequence.size() + " · 断言 " + evaluation.assertions().size()
                + " 条 · 通过=" + evaluation.passed());
        evaluation.failures().forEach(f -> writer.println("      不通过 " + f.packetType() + "." + f.field()
                + " 期望 " + f.expected() + " 实际 " + f.actual() + "（" + f.diff() + "）"));

        return evaluation.passed() && matrix.failedCells().isEmpty() ? 0 : 1;
    }

    /** 断言「类型 + 关键字段」：实体位置、区块数据、物品栈是反作弊跨版本回归的核心点。 */
    private static List<PacketAssertionSpec> assertionSpecs() {
        return List.of(
                new PacketAssertionSpec("LOGIN_SUCCESS", "protocolVersion", FieldMatcher.inRange(700, 800)),
                new PacketAssertionSpec("JOIN_GAME", "viewDistance", FieldMatcher.inRange(2, 32)),
                new PacketAssertionSpec("CHUNK_DATA", "chunkX", FieldMatcher.eq(0)),
                new PacketAssertionSpec("ENTITY_POSITION", "entityId", FieldMatcher.eq(42)),
                new PacketAssertionSpec("SET_SLOT", "amount", FieldMatcher.inRange(1, 64)),
                new PacketAssertionSpec("CHAT", "type", FieldMatcher.contains("CHAT")));
    }
}
