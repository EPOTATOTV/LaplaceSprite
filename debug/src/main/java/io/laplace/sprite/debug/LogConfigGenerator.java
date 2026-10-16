package io.laplace.sprite.debug;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * 日志配置生成器（15.1）：按 modid 生成 log4j2 模板（log4j-dev.xml），把该 modid 开到 debug 级。
 *
 * <p>只开被测 modid 的 debug，Root 保持 info：否则客户端日志会被 MC 自身刷屏，断点对照反而更难。</p>
 */
public final class LogConfigGenerator {

    public static final String FILE_NAME = "log4j-dev.xml";

    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    private static final String TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Configuration status="WARN">
              <Appenders>
                <Console name="Console" target="SYSTEM_OUT">
                  <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] (%logger{1}) %msg%n"/>
                </Console>
                <File name="DevFile" fileName="logs/dev-__MODID__.log">
                  <PatternLayout pattern="[%d{HH:mm:ss}] [%t/%level] (%logger{1}) %msg%n"/>
                </File>
              </Appenders>
              <Loggers>
                <Logger name="__MODID__" level="debug" additivity="false">
                  <AppenderRef ref="Console"/>
                  <AppenderRef ref="DevFile"/>
                </Logger>
                <Root level="info">
                  <AppenderRef ref="Console"/>
                </Root>
              </Loggers>
            </Configuration>
            """;

    public String log4j2Xml(String modid) {
        requireModId(modid);
        return TEMPLATE.replace("__MODID__", modid);
    }

    /** 写到指定目录下的 log4j-dev.xml。 */
    public Path write(Path directory, String modid) {
        Path target = directory.resolve(FILE_NAME);
        try {
            Files.createDirectories(directory);
            Files.writeString(target, log4j2Xml(modid), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("写入日志配置失败: " + target, ex);
        }
        return target;
    }

    /** modid 规则按加载器一致：全小写字母数字下划线，2-64 位，首字符为字母。 */
    private static void requireModId(String modid) {
        if (modid == null || !MOD_ID.matcher(modid).matches()) {
            throw new IllegalArgumentException("非法 modid: " + modid);
        }
    }
}
