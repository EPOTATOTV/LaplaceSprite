package io.laplace.sprite.profile;

import java.nio.file.Path;
import java.util.Objects;

/**
 * JFR 录制器（15.3）：压测类用例固定落 JFR，走 {@code jcmd} 下发 JFR.start / JFR.dump / JFR.stop。
 *
 * <p>本类只负责渲染命令行与记住目标文件路径，不真拉起进程——真实执行由沙箱/驱动器侧完成。</p>
 */
public final class JfrRecorder {

    /** jcmd 可执行文件名，可用 -D 或构造参数替换为绝对路径。 */
    public static final String DEFAULT_JCMD_BINARY = "jcmd";

    private final Path outputDir;
    private final String jcmdBinary;

    public JfrRecorder(Path outputDir) {
        this(outputDir, DEFAULT_JCMD_BINARY);
    }

    public JfrRecorder(Path outputDir, String jcmdBinary) {
        this.outputDir = Objects.requireNonNull(outputDir, "outputDir");
        this.jcmdBinary = jcmdBinary == null || jcmdBinary.isBlank() ? DEFAULT_JCMD_BINARY : jcmdBinary;
    }

    public Path outputDir() {
        return outputDir;
    }

    public String jcmdBinary() {
        return jcmdBinary;
    }

    /** 录制名：同一实例固定，便于 dump / stop 对上号。 */
    public String recordingName(String instanceId) {
        return "laplace-" + instanceId;
    }

    /** JFR 落盘目标文件。 */
    public Path targetFile(String instanceId) {
        return outputDir.resolve(recordingName(instanceId) + ".jfr");
    }

    /** jcmd &lt;pid&gt; JFR.start name=... filename=... settings=profile。 */
    public String startCommand(long pid, String instanceId) {
        return "%s %d JFR.start name=%s filename=%s settings=profile".formatted(
                jcmdBinary, pid, recordingName(instanceId), targetFile(instanceId));
    }

    /** jcmd &lt;pid&gt; JFR.dump name=... filename=...：不停录制，先落一份现场。 */
    public String dumpCommand(long pid, String instanceId) {
        return "%s %d JFR.dump name=%s filename=%s".formatted(
                jcmdBinary, pid, recordingName(instanceId), targetFile(instanceId));
    }

    /** jcmd &lt;pid&gt; JFR.stop name=...：用例结束收尾。 */
    public String stopCommand(long pid, String instanceId) {
        return "%s %d JFR.stop name=%s".formatted(jcmdBinary, pid, recordingName(instanceId));
    }
}
