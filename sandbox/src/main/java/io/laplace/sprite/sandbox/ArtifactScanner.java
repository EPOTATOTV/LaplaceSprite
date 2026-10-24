package io.laplace.sprite.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 制品扫描（v3.0 L3-04）：只读流检查 jar/zip，不执行、不解压到磁盘。
 *
 * <p>路径逃逸、体积异常、可执行头、脚本 shebang、后缀不在白名单都直接判不干净；
 * {@code Runtime.exec}、{@code ProcessBuilder}、{@code URLClassLoader}、shell 命令与超长 base64
 * 只是可疑特征，同样计入 findings 并让结果不干净，但原因分开列出，便于人工复核误报。</p>
 */
public final class ArtifactScanner {

    static final int MAX_ENTRIES = 10_000;
    static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;

    private static final Set<String> ALLOWED_SUFFIXES = Set.of(
            ".jar", ".json", ".yaml", ".yml", ".properties", ".toml", ".mcmeta", ".png", ".ogg",
            ".txt", ".lang", ".mcfunction", ".nbt", ".class", ".mf", ".sf", ".rsa", ".dsa", ".ec",
            ".md", ".xml");

    private static final List<String> SUSPICIOUS_MARKERS = List.of(
            "runtime.getruntime().exec", "processbuilder", "urlclassloader", "powershell", "cmd.exe");

    private static final int BASE64_RUN_LIMIT = 512;
    private static final int WINDOW_CHARS = 64;
    private static final int BUFFER_BYTES = 8192;

    /** 扫描结论；{@code findings} 为空即干净，否则每条都是拒绝或需复核的原因。 */
    public record ScanResult(boolean clean, List<String> findings, int entries, long uncompressedBytes) {
        public ScanResult {
            findings = List.copyOf(findings);
        }
    }

    public ScanResult scan(Path artifact) throws IOException {
        if (artifact == null || !Files.isRegularFile(artifact)) {
            throw new IOException("制品不存在或不是普通文件：" + artifact);
        }
        Set<String> findings = new LinkedHashSet<>();
        int entries = 0;
        long totalBytes = 0;

        try (ZipFile zip = new ZipFile(artifact.toFile(), StandardCharsets.UTF_8)) {
            Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                ZipEntry entry = iterator.nextElement();
                entries++;
                if (entries > MAX_ENTRIES) {
                    findings.add("条目数超过阈值 " + MAX_ENTRIES);
                    break;
                }
                String name = entry.getName();
                checkName(name, findings);
                if (!entry.isDirectory()) {
                    checkSuffix(name, findings);
                }
                if (entry.getSize() > MAX_UNCOMPRESSED_BYTES) {
                    findings.add("条目 " + name + " 声明解压大小超过阈值 " + MAX_UNCOMPRESSED_BYTES + " 字节");
                    continue;
                }
                totalBytes += scanEntry(zip, entry, findings);
                if (totalBytes > MAX_UNCOMPRESSED_BYTES) {
                    findings.add("解压后总量超过阈值 " + MAX_UNCOMPRESSED_BYTES + " 字节");
                    break;
                }
            }
        }
        return new ScanResult(findings.isEmpty(), new ArrayList<>(findings), entries, totalBytes);
    }

    private static void checkName(String name, Set<String> findings) {
        if (name == null || name.isEmpty()) {
            findings.add("存在空条目名");
            return;
        }
        if (name.indexOf('\\') >= 0) {
            findings.add("条目名含反斜杠，可能是路径逃逸：" + name);
        }
        String normalized = name.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            findings.add("绝对路径条目：" + name);
        }
        for (String segment : normalized.split("/")) {
            if ("..".equals(segment)) {
                findings.add("条目名含 .. ：" + name);
                break;
            }
        }
    }

    private static void checkSuffix(String name, Set<String> findings) {
        if (!isAllowedEntry(name)) {
            findings.add("条目后缀不在允许集合：" + name);
        }
    }

    /**
     * jar 元数据与 ServiceLoader 描述符没有后缀，不能按扩展名一刀切。
     * 只放行明确的元数据路径，避免把无后缀可执行文件也带进来。
     */
    private static boolean isAllowedEntry(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String fileName = lower.substring(lower.lastIndexOf('/') + 1);
        if (ALLOWED_SUFFIXES.stream().anyMatch(lower::endsWith)) {
            return true;
        }
        if (lower.startsWith("meta-inf/services/")) {
            return true;
        }
        return fileName.equals("license") || fileName.equals("notice") || fileName.equals("copying")
                || fileName.equals("readme") || fileName.equals("changelog");
    }

    private static long scanEntry(ZipFile zip, ZipEntry entry, Set<String> findings) throws IOException {
        if (entry.isDirectory()) {
            return 0;
        }
        byte[] head = new byte[4];
        int headLength = 0;
        String carry = "";
        int base64Run = 0;
        boolean base64Reported = false;
        Set<String> markersFound = new HashSet<>();
        long counted = 0;

        try (InputStream in = zip.getInputStream(entry)) {
            byte[] buffer = new byte[BUFFER_BYTES];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (headLength < head.length) {
                    int copy = Math.min(read, head.length - headLength);
                    System.arraycopy(buffer, 0, head, headLength, copy);
                    headLength += copy;
                }
                counted += read;
                String chunk = new String(buffer, 0, read, StandardCharsets.ISO_8859_1);
                String window = carry + chunk;
                String lower = window.toLowerCase(Locale.ROOT);
                for (String marker : SUSPICIOUS_MARKERS) {
                    if (!markersFound.contains(marker) && lower.contains(marker)) {
                        markersFound.add(marker);
                        findings.add("条目 " + entry.getName() + " 含可疑特征 " + marker);
                    }
                }
                if (!base64Reported) {
                    for (int i = carry.length(); i < window.length(); i++) {
                        if (isBase64Char(window.charAt(i))) {
                            base64Run++;
                            if (base64Run >= BASE64_RUN_LIMIT) {
                                findings.add("条目 " + entry.getName() + " 含超长 base64 片段（"
                                        + BASE64_RUN_LIMIT + " 字符以上）");
                                base64Reported = true;
                                break;
                            }
                        } else {
                            base64Run = 0;
                        }
                    }
                }
                carry = window.length() > WINDOW_CHARS
                        ? window.substring(window.length() - WINDOW_CHARS) : window;
                if (counted > MAX_UNCOMPRESSED_BYTES) {
                    findings.add("条目 " + entry.getName() + " 实际解压大小超过阈值");
                    break;
                }
            }
        }
        checkMagic(entry.getName(), head, headLength, findings);
        return counted;
    }

    private static boolean isBase64Char(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')
                || (value >= '0' && value <= '9') || value == '+' || value == '/' || value == '=';
    }

    private static void checkMagic(String name, byte[] head, int length, Set<String> findings) {
        if (length < 2) {
            return;
        }
        if (head[0] == '#' && head[1] == '!') {
            findings.add("条目 " + name + " 以脚本 shebang #! 开头");
            return;
        }
        if (head[0] == 'M' && head[1] == 'Z') {
            findings.add("条目 " + name + " 以 MZ 可执行头开头");
            return;
        }
        if (length < 4) {
            return;
        }
        int b0 = head[0] & 0xff;
        int b1 = head[1] & 0xff;
        int b2 = head[2] & 0xff;
        int b3 = head[3] & 0xff;
        if (b0 == 0x7f && b1 == 'E' && b2 == 'L' && b3 == 'F') {
            findings.add("条目 " + name + " 以 ELF 可执行头开头");
            return;
        }
        // Java class 文件本身就是 CAFEBABE，与 Mach-O 的通用二进制头重名；.class 已是白名单后缀，
        // 对这类条目不再按 Mach-O 判，但 MZ / ELF / shebang 仍照常检查。
        if (name.toLowerCase(Locale.ROOT).endsWith(".class")) {
            return;
        }
        boolean machO = (b0 == 0xfe && b1 == 0xed && b2 == 0xfa && b3 == 0xce)
                || (b0 == 0xfe && b1 == 0xed && b2 == 0xfa && b3 == 0xcf)
                || (b0 == 0xca && b1 == 0xfe && b2 == 0xba && b3 == 0xbe);
        if (machO) {
            findings.add("条目 " + name + " 以 Mach-O 可执行头开头");
        }
    }
}
