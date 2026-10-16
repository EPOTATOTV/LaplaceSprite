package io.laplace.sprite.servers.clienttest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Loom 任务渲染（15.7）：只渲染调用形态，不声称跑过真实客户端。 */
class LoomTaskRendererTest {

    private final LoomTaskRenderer renderer = new LoomTaskRenderer("gradlew");

    @Test
    void plainInvocationHasNoExtraProperty() {
        assertEquals(List.of("gradlew", "--build-cache", "runClientGameTest"), renderer.render(false, false));
    }

    @Test
    void syncWorkaroundAppendsSystemProperty() {
        List<String> command = renderer.render(true, false);

        assertTrue(command.contains(LoomTaskRenderer.SYNC_WORKAROUND_PROPERTY));
        assertEquals(LoomTaskRenderer.TASK, command.get(command.size() - 1));
    }

    @Test
    void xvfbWrapperWrapsTheWholeInvocation() {
        List<String> command = renderer.render(true, true);

        assertEquals(LoomTaskRenderer.XVFB_EXECUTABLE, command.get(0));
        assertEquals("-a", command.get(1));
        assertEquals("-s", command.get(2));
        assertEquals(LoomTaskRenderer.XVFB_SCREEN, command.get(3));
        assertEquals("gradlew", command.get(4));
        assertEquals("xvfb-run -a -s -screen 0 1280x720x24 gradlew --build-cache "
                + LoomTaskRenderer.SYNC_WORKAROUND_PROPERTY + " runClientGameTest", renderer.renderLine(true, true));
    }
}
