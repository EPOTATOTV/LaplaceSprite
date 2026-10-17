package io.laplace.sprite.replay;

import io.laplace.sprite.loadtest.BotAction;
import io.laplace.sprite.loadtest.BotBackend;
import io.laplace.sprite.loadtest.LoadScenario;
import io.laplace.sprite.loadtest.RampCurve;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * 从录像抽取可重放脚本（15.10）。
 *
 * <p>只按 {@link ReplayPacketIds} 登记的协议包 id 取数；遇到不认识的包直接跳过，
 * 不按载荷长度猜包类型。移动包按 tick 合并：同一 tick 的位置包与视角包互相补全，
 * 最终每 tick 留一个位姿。</p>
 *
 * <p>抽出的脚本通过 {@link #toLoadScenario} 映射到压测轨的 {@code LoadScenario} /
 * {@code BotAction} 词汇，由 15.9 的假玩家轨执行。</p>
 */
public final class ReplayScriptExtractor {

    private final ReplayPacketIds packetIds;

    public ReplayScriptExtractor() {
        this(ReplayPacketIds.mc1201());
    }

    public ReplayScriptExtractor(ReplayPacketIds packetIds) {
        this.packetIds = Objects.requireNonNull(packetIds, "packetIds");
    }

    public ReplayScript extract(McprArchive archive, String sourceMcpr, String worldSnapshotRef) {
        TreeMap<Integer, MovementState> byTick = new TreeMap<>();
        List<CommandEntry> commands = new ArrayList<>();

        for (PacketRecord packet : archive.packets()) {
            int packetId = packet.packetId();
            MovementState previous = lastState(byTick);
            if (packetId == packetIds.position()) {
                readPosition(packet.payload(), previous).ifPresent(state -> byTick.put(packet.tick(), state));
            } else if (packetId == packetIds.positionLook()) {
                readPositionLook(packet.payload(), previous)
                        .ifPresent(state -> byTick.put(packet.tick(), state));
            } else if (packetId == packetIds.look()) {
                readLook(packet.payload(), previous).ifPresent(state -> byTick.put(packet.tick(), state));
            } else if (packetId == packetIds.onGround()) {
                readOnGround(packet.payload(), previous)
                        .ifPresent(state -> byTick.put(packet.tick(), state));
            } else if (packetId == packetIds.chatCommand()) {
                readText(packet.payload())
                        .ifPresent(text -> commands.add(new CommandEntry(packet.tick(), text, true)));
            } else if (packetId == packetIds.chatMessage()) {
                readText(packet.payload())
                        .ifPresent(text -> commands.add(new CommandEntry(packet.tick(), text, false)));
            }
        }

        List<MovementSample> samples = new ArrayList<>(byTick.size());
        byTick.forEach((tick, state) -> samples.add(new MovementSample(tick, state.x(), state.y(), state.z(),
                state.yaw(), state.pitch(), state.onGround())));
        return new ReplayScript(sourceMcpr, archive.mcVersion(), worldSnapshotRef, archive.playerName(),
                new PlayerMovementTrack(samples), new CommandTrack(commands));
    }

    public LoadScenario toLoadScenario(ReplayScript script, String scenarioId) {
        return toLoadScenario(script, scenarioId, RampCurve.single(1));
    }

    /**
     * 映射成压测剧本。
     *
     * <p>回放由服务端内假人执行，后端固定 CARPET；默认单假人、恒定并发。动作按轨迹与指令
     * 归并成 {@link BotAction}，未知指令落到 CHAT，保留指令流语义。</p>
     */
    public LoadScenario toLoadScenario(ReplayScript script, String scenarioId, RampCurve ramp) {
        Set<BotAction> actions = new LinkedHashSet<>();
        actions.add(BotAction.JOIN);
        if (!script.movement().isEmpty()) {
            actions.add(BotAction.WALK);
        }
        for (CommandEntry entry : script.commands().entries()) {
            actions.add(actionOf(entry));
        }
        if (actions.size() == 1) {
            actions.add(BotAction.IDLE);
        }
        return new LoadScenario(scenarioId, BotBackend.CARPET, List.copyOf(actions),
                script.durationTicks(), ramp, "由 .mcpr 回放脚本生成：" + script.sourceMcpr());
    }

    static BotAction actionOf(CommandEntry entry) {
        if (!entry.command()) {
            return BotAction.CHAT;
        }
        String text = entry.text().toLowerCase(Locale.ROOT);
        if (text.contains("mine") || text.contains("dig") || text.contains("break")) {
            return BotAction.MINE;
        }
        if (text.contains("place") || text.contains("setblock") || text.contains("fill")
                || text.contains("use")) {
            return BotAction.PLACE;
        }
        if (text.contains("attack") || text.contains("kill") || text.contains("hit")) {
            return BotAction.ATTACK;
        }
        return BotAction.CHAT;
    }

    private static MovementState lastState(TreeMap<Integer, MovementState> byTick) {
        return byTick.isEmpty() ? MovementState.origin() : byTick.lastEntry().getValue();
    }

    private static Optional<MovementState> readPosition(byte[] payload, MovementState previous) {
        // 三个 double 加一个布尔，共 25 字节；后续版本追加的字段忽略
        if (payload.length < 25) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        double x = buffer.getDouble();
        double y = buffer.getDouble();
        double z = buffer.getDouble();
        boolean onGround = buffer.get() != 0;
        return Optional.of(new MovementState(x, y, z, previous.yaw(), previous.pitch(), onGround));
    }

    private static Optional<MovementState> readPositionLook(byte[] payload, MovementState previous) {
        // 三个 double、两个 float 加一个布尔，共 33 字节
        if (payload.length < 33) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        double x = buffer.getDouble();
        double y = buffer.getDouble();
        double z = buffer.getDouble();
        float yaw = buffer.getFloat();
        float pitch = buffer.getFloat();
        boolean onGround = buffer.get() != 0;
        return Optional.of(new MovementState(x, y, z, yaw, pitch, onGround));
    }

    private static Optional<MovementState> readLook(byte[] payload, MovementState previous) {
        // 两个 float 加一个布尔，共 9 字节
        if (payload.length < 9) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        float yaw = buffer.getFloat();
        float pitch = buffer.getFloat();
        boolean onGround = buffer.get() != 0;
        return Optional.of(new MovementState(previous.x(), previous.y(), previous.z(), yaw, pitch,
                onGround));
    }

    private static Optional<MovementState> readOnGround(byte[] payload, MovementState previous) {
        if (payload.length < 1) {
            return Optional.empty();
        }
        return Optional.of(new MovementState(previous.x(), previous.y(), previous.z(), previous.yaw(),
                previous.pitch(), payload[0] != 0));
    }

    /** 聊天 / 指令包的首字段是 VarInt 长度前缀的字符串。 */
    private static Optional<String> readText(byte[] payload) {
        int[] cursor = {0};
        Integer length = readVarInt(payload, cursor);
        if (length == null || length < 0 || cursor[0] + length > payload.length) {
            return Optional.empty();
        }
        String text = new String(payload, cursor[0], length, StandardCharsets.UTF_8);
        return text.isBlank() ? Optional.empty() : Optional.of(text);
    }

    private static Integer readVarInt(byte[] payload, int[] cursor) {
        int value = 0;
        int shift = 0;
        while (cursor[0] < payload.length && shift < 35) {
            int current = payload[cursor[0]++] & 0xFF;
            value |= (current & 0x7F) << shift;
            if ((current & 0x80) == 0) {
                return value;
            }
            shift += 7;
        }
        return null;
    }

    private record MovementState(double x, double y, double z, float yaw, float pitch,
                                 boolean onGround) {

        private static MovementState origin() {
            return new MovementState(0.0, 0.0, 0.0, 0.0f, 0.0f, false);
        }
    }
}
