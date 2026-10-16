package io.laplace.sprite.debug;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 日志配置生成器（15.1）：log4j2 模板要能解析，且只把被测 modid 开到 debug。 */
class LogConfigGeneratorTest {

    @Test
    void templateForModidParsesAndCarriesDebugLevel(@TempDir Path tempDir) throws Exception {
        LogConfigGenerator generator = new LogConfigGenerator();
        String xml = generator.log4j2Xml("example_mod");

        Document document = parse(xml);
        assertEquals("Configuration", document.getDocumentElement().getTagName());
        Element logger = (Element) document.getElementsByTagName("Logger").item(0);
        assertEquals("example_mod", logger.getAttribute("name"));
        assertEquals("debug", logger.getAttribute("level"));
        assertEquals("info", ((Element) document.getElementsByTagName("Root").item(0)).getAttribute("level"),
                "Root 保持 info，避免 MC 自身刷屏");
        assertTrue(xml.contains("logs/dev-example_mod.log"));

        Path written = generator.write(tempDir, "example_mod");
        assertEquals("log4j-dev.xml", written.getFileName().toString());
        assertTrue(Files.readString(written).contains("example_mod"));
        parse(Files.readString(written));
    }

    @Test
    void invalidModIdsAreRejected() {
        LogConfigGenerator generator = new LogConfigGenerator();
        assertThrows(IllegalArgumentException.class, () -> generator.log4j2Xml("Example"));
        assertThrows(IllegalArgumentException.class, () -> generator.log4j2Xml("a"));
        assertThrows(IllegalArgumentException.class, () -> generator.log4j2Xml("bad-mod"));
        assertThrows(IllegalArgumentException.class, () -> generator.log4j2Xml(null));
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }
}
