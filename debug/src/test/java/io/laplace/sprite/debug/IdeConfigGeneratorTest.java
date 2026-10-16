package io.laplace.sprite.debug;

import io.laplace.sprite.core.model.PlatformFamily;
import io.laplace.sprite.core.model.PlatformKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IDE 配置生成器（15.1）：产出的 XML 必须能被解析，且带着 jdwp 端口。 */
class IdeConfigGeneratorTest {

    @Test
    void remoteConfigParsesAndCarriesJdwpPort(@TempDir Path tempDir) throws Exception {
        IdeConfigGenerator generator = new IdeConfigGenerator();
        String xml = generator.remoteRunConfig("Paper 插件调试", "paper-plugin", JdwpOptions.ciDefault(5005),
                "$PROJECT_DIR$/server");

        Document document = parse(xml);
        assertEquals("component", document.getDocumentElement().getTagName());
        assertEquals("ProjectRunConfigurationManager", document.getDocumentElement().getAttribute("name"));

        NodeList configurations = document.getElementsByTagName("configuration");
        assertEquals(1, configurations.getLength());
        Element configuration = (Element) configurations.item(0);
        assertEquals("Remote", configuration.getAttribute("type"));
        assertEquals("Paper 插件调试", configuration.getAttribute("name"));
        assertEquals("paper-plugin", ((Element) configuration.getElementsByTagName("module").item(0))
                .getAttribute("name"));
        assertEquals("5005", optionValue(configuration, "PORT"));
        assertEquals("localhost", optionValue(configuration, "HOST"));
        assertEquals("true", optionValue(configuration, "USE_SOCKET_TRANSPORT"));
        assertEquals("$PROJECT_DIR$/server", optionValue(configuration, "WORKING_DIRECTORY"));
        assertTrue(xml.contains("5005"));
        assertTrue(tempDir.toFile().isDirectory());
    }

    @Test
    void writePlacesFileUnderRunDirectory(@TempDir Path tempDir) throws Exception {
        IdeConfigGenerator generator = new IdeConfigGenerator();
        Path written = generator.write(tempDir, "Fabric 模组调试", "example-mod", JdwpOptions.waitForIde(5006),
                "$PROJECT_DIR$/server");

        assertEquals(IdeConfigGenerator.RUN_DIR, written.getParent().getFileName().toString());
        assertEquals(".run", written.getParent().getFileName().toString());
        assertTrue(Files.isRegularFile(written));
        assertTrue(Files.readString(written).contains("5006"));
        parse(Files.readString(written));
    }

    @Test
    void productTypeDrivesConfigNameAndWorkingDirectory() {
        IdeConfigGenerator generator = new IdeConfigGenerator();
        PlatformKey fabric = PlatformKey.of(PlatformFamily.FABRIC, "1.21.11");
        PlatformKey paper = PlatformKey.of(PlatformFamily.PAPER, "1.21.11");
        PlatformKey velocity = PlatformKey.of(PlatformFamily.VELOCITY, "3.3.0");

        String modXml = generator.remoteRunConfigFor(fabric, "example-mod", JdwpOptions.ciDefault(5005));
        assertTrue(modXml.contains("example-mod（模组调试）"));
        assertTrue(modXml.contains("$PROJECT_DIR$/server"), "模组落在 server/mods，服务端根取上一级");

        String pluginXml = generator.remoteRunConfigFor(paper, "example-plugin", JdwpOptions.ciDefault(5006));
        assertTrue(pluginXml.contains("example-plugin（插件调试）"));
        assertTrue(pluginXml.contains("5006"));
        assertTrue(pluginXml.contains("$PROJECT_DIR$/server"));

        assertTrue(generator.configNameFor(velocity, "example-proxy").contains("代理调试"));
    }

    @Test
    void xmlSpecialCharactersAreEscaped() throws Exception {
        IdeConfigGenerator generator = new IdeConfigGenerator();
        String xml = generator.remoteRunConfig("a<b>&c", "mod", JdwpOptions.ciDefault(5005), "$PROJECT_DIR$/x");
        assertEquals("a<b>&c", ((Element) parse(xml).getElementsByTagName("configuration").item(0))
                .getAttribute("name"));
    }

    @Test
    void blankInputsAreRejected() {
        IdeConfigGenerator generator = new IdeConfigGenerator();
        JdwpOptions options = JdwpOptions.ciDefault(5005);
        assertThrows(IllegalArgumentException.class,
                () -> generator.remoteRunConfig(" ", "m", options, "wd"));
        assertThrows(IllegalArgumentException.class,
                () -> generator.remoteRunConfig("name", " ", options, "wd"));
        assertThrows(IllegalArgumentException.class,
                () -> generator.remoteRunConfig("name", "m", options, " "));
    }

    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
    }

    private static String optionValue(Element configuration, String name) {
        NodeList options = configuration.getElementsByTagName("option");
        for (int i = 0; i < options.getLength(); i++) {
            Element option = (Element) options.item(i);
            if (name.equals(option.getAttribute("name"))) {
                return option.getAttribute("value");
            }
        }
        return null;
    }
}
