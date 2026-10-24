package io.laplace.sprite.core.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 内核多语言：默认语言、切换、回退、格式化、插件语言包与别名解析。 */
class I18nTest {

    private final I18n i18n = I18n.get();

    @BeforeEach
    @AfterEach
    void resetState() {
        i18n.reset();
    }

    @Test
    void defaultsToChinese() {
        assertEquals(Language.ZH_CN, i18n.language());
        assertEquals("未发现业务插件", i18n.t("plugin.list.empty"));
        assertEquals("未知", i18n.t("game.metadata.unknown"));
    }

    @Test
    void switchesToEnglish() {
        i18n.setLanguage(Language.EN_US);

        assertEquals(Language.EN_US, i18n.language());
        assertEquals("No business plugins discovered", i18n.t("plugin.list.empty"));
        assertEquals("Laplace Sprite matrix alert: 2 item(s)", i18n.t("alert.subject", 2));
    }

    @Test
    void fallsBackToEnglishThenToKey() {
        i18n.registerPluginBundle("demo", Language.EN_US, Map.of("greeting", "Hello"));

        assertEquals("Hello", i18n.t("demo.greeting"));
        assertEquals("no.such.key", i18n.t("no.such.key"));
    }

    @Test
    void formatsArgumentsWithMessageFormat() {
        assertEquals("业务插件（3 个）", i18n.t("plugin.list.header", 3));

        i18n.setLanguage(Language.EN_US);
        assertEquals("Business plugins (3)", i18n.t("plugin.list.header", 3));
        assertEquals("  client.jar 1024 bytes · 2 attempts · SHA-1 abc · SHA-256 def",
                "  " + i18n.t("game.download.ok", "client.jar", "1024", 2, "abc", "def"));
    }

    @Test
    void pluginBundlesGetPrefixAndOverwriteOnReregister() {
        i18n.setLanguage(Language.EN_US);
        i18n.registerPluginBundle("demo", Language.EN_US, Map.of("greeting", "Hello {0}"));

        assertEquals("Hello Ada", i18n.t("demo.greeting", "Ada"));
        assertTrue(i18n.keyCount() > 0);

        i18n.registerPluginBundle("demo", Language.EN_US, Map.of("demo.greeting", "Bye {0}"));
        assertEquals("Bye Ada", i18n.t("demo.greeting", "Ada"));
    }

    @Test
    void resetRestoresDefaultLanguageAndDropsPluginBundles() {
        i18n.setLanguage(Language.EN_US);
        i18n.registerPluginBundle("demo", Language.EN_US, Map.of("greeting", "Hello"));

        i18n.reset();

        assertEquals(Language.ZH_CN, i18n.language());
        assertEquals("demo.greeting", i18n.t("demo.greeting"));
    }

    @Test
    void parsesLanguageAliases() {
        assertEquals(Optional.of(Language.ZH_CN), Language.parse("zh-CN"));
        assertEquals(Optional.of(Language.ZH_CN), Language.parse("zh"));
        assertEquals(Optional.of(Language.ZH_CN), Language.parse("ZH_cn"));
        assertEquals(Optional.of(Language.EN_US), Language.parse("en-US"));
        assertEquals(Optional.of(Language.EN_US), Language.parse("en"));
        assertFalse(Language.parse("fr").isPresent());
        assertFalse(Language.parse(null).isPresent());
    }
}
