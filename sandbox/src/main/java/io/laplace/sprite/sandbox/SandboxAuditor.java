package io.laplace.sprite.sandbox;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 敏感路径审计（15.4）：启动前检查沙箱 spec 到底把宿主机的哪些目录交给了容器。
 *
 * <p>只读挂载同样能泄漏宿主环境（~/.ssh、docker 套接字、盘符根目录），所以审计与读写无关，
 * 命中即拒绝启动。命中判定只做路径字符串匹配，不碰文件系统，便于在 CI 里稳定复现。</p>
 */
public final class SandboxAuditor {

    private static final List<String> CREDENTIAL_DIRS = List.of(
            "/.ssh", "/.aws", "/.kube", "/.gnupg", "/.docker/config.json", "/.netrc", "/.npmrc");

    private static final List<String> CREDENTIAL_FILE_SUFFIXES = List.of(
            ".pem", ".key", ".p12", ".pfx", ".jks", ".p8");

    private static final List<String> SYSTEM_PATHS = List.of(
            "/var/run/docker.sock", "/var/lib/docker", "/proc", "/sys", "/boot", "/root",
            "/usr", "/bin", "/sbin",
            "c:/windows", "c:/program files", "c:/program files (x86)");

    /** 逐条审计挂载点与可写数据目录，命中与否都返回，报告里能看出审了什么。 */
    public List<SandboxAudit> audit(SandboxSpec spec) {
        Set<String> paths = new LinkedHashSet<>();
        spec.readOnlyMounts().forEach(mount -> paths.add(mount.hostPath()));
        paths.add(spec.writableDataDir().toString());
        List<SandboxAudit> audits = new ArrayList<>(paths.size());
        paths.forEach(path -> audits.add(auditPath(path)));
        return List.copyOf(audits);
    }

    public List<SandboxAudit> sensitive(SandboxSpec spec) {
        return audit(spec).stream().filter(SandboxAudit::sensitive).toList();
    }

    public boolean clean(SandboxSpec spec) {
        return sensitive(spec).isEmpty();
    }

    public SandboxAudit auditPath(String hostPath) {
        if (hostPath == null || hostPath.isBlank()) {
            throw new IllegalArgumentException("被审计路径不能为空");
        }
        String path = normalize(hostPath);
        List<String> reasons = new ArrayList<>();

        if (path.matches("[a-z]:")) {
            reasons.add("Windows 盘符根目录");
        }
        if (path.equals("/etc") || path.startsWith("/etc/")) {
            reasons.add("系统配置目录 /etc");
        }
        for (String dir : CREDENTIAL_DIRS) {
            if (path.equals(dir) || path.contains(dir + "/") || path.endsWith(dir)) {
                reasons.add("凭据目录 " + dir);
            }
        }
        for (String system : SYSTEM_PATHS) {
            if (path.equals(system) || path.startsWith(system + "/")) {
                reasons.add("系统路径 " + system);
            }
        }
        for (String suffix : CREDENTIAL_FILE_SUFFIXES) {
            if (path.endsWith(suffix)) {
                reasons.add("密钥/证书文件 " + suffix);
            }
        }
        String home = normalize(System.getProperty("user.home", ""));
        if (!home.isEmpty() && path.equals(home)) {
            reasons.add("用户主目录");
        }
        String appData = normalize(System.getenv("APPDATA") == null ? "" : System.getenv("APPDATA"));
        if (!appData.isEmpty() && path.startsWith(appData + "/") && containsCredentialWord(path)) {
            reasons.add("%APPDATA% 凭据目录");
        }

        if (reasons.isEmpty()) {
            return SandboxAudit.clean(hostPath);
        }
        return SandboxAudit.sensitive(hostPath, String.join("、", reasons));
    }

    private static boolean containsCredentialWord(String path) {
        return path.contains("credential") || path.contains("token") || path.contains("secret");
    }

    /** 统一成小写正斜杠、去掉尾部斜杠，Windows 与 Linux 声明写法都能比对。 */
    private static String normalize(String raw) {
        String path = raw.replace('\\', '/').toLowerCase(Locale.ROOT);
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
