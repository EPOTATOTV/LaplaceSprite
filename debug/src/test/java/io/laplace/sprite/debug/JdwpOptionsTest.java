package io.laplace.sprite.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** jdwp 注入（15.1）：两种挂起模式、地址写法、端口校验。 */
class JdwpOptionsTest {

    @Test
    void ciDefaultUsesSuspendNone() {
        JdwpOptions options = JdwpOptions.ciDefault(5005);

        assertEquals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005", options.toAgentArg());
        assertFalse(options.waitsForIde(), "CI 默认不挂起");
        assertEquals("127.0.0.1:5005:5005", options.publishArgument());
    }

    @Test
    void waitForIdeUsesSuspendYes() {
        JdwpOptions options = JdwpOptions.waitForIde(8000);

        assertEquals("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000", options.toAgentArg());
        assertTrue(options.waitsForIde());
    }

    @Test
    void hostAddressIsRenderedWhenPresent() {
        JdwpOptions options = JdwpOptions.ciDefault(5005).boundTo("0.0.0.0");

        assertEquals("0.0.0.0", options.hostAddress());
        assertTrue(options.toAgentArg().endsWith("address=0.0.0.0:5005"));
    }

    @Test
    void portRangeIsValidated() {
        assertThrows(IllegalArgumentException.class, () -> JdwpOptions.ciDefault(80));
        assertThrows(IllegalArgumentException.class, () -> JdwpOptions.ciDefault(70000));
        assertEquals(1024, JdwpOptions.ciDefault(1024).port());
        assertEquals(65535, JdwpOptions.ciDefault(65535).port());
        assertEquals("", new JdwpOptions(5005, false, null).hostAddress());
    }
}
