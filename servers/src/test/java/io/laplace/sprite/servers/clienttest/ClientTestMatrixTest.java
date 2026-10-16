package io.laplace.sprite.servers.clienttest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 客户端矩阵（15.7）：维度顺序、组合数与 XVFB 自动启用。 */
class ClientTestMatrixTest {

    @Test
    void combinationsFollowMcLoaderJdkOsOrder() {
        ClientTestMatrix matrix = new ClientTestMatrix(List.of("1.21.1"), List.of("fabric", "forge"),
                List.of("21"), List.of("linux", "windows"), name -> null);

        List<ClientMatrixCell> cells = matrix.combinations();
        assertEquals(4, cells.size());
        assertEquals(4, matrix.size());
        assertEquals("1.21.1/fabric/21/linux", cells.get(0).key());
        assertEquals("1.21.1/fabric/21/windows", cells.get(1).key());
        assertEquals("1.21.1/forge/21/linux", cells.get(2).key());
        assertEquals("1.21.1/forge/21/windows", cells.get(3).key());
    }

    @Test
    void defaultMatrixCoversAllFourDimensions() {
        ClientTestMatrix matrix = ClientTestMatrix.defaults();

        assertEquals(ClientTestMatrix.DEFAULT_MC_VERSIONS.size() * ClientTestMatrix.DEFAULT_LOADERS.size()
                * ClientTestMatrix.DEFAULT_JDKS.size() * ClientTestMatrix.DEFAULT_OSES.size(), matrix.size());
        assertTrue(matrix.combinations().stream().map(ClientMatrixCell::mcVersion).distinct().count() > 1);
        assertEquals(ClientTestMatrix.DEFAULT_LOADERS,
                matrix.combinations().stream().map(ClientMatrixCell::loader).distinct().toList());
    }

    @Test
    void xvfbIsAutoEnabledOnlyUnderCiEnv() {
        assertFalse(matrix(name -> null).useXvfb(), "非 CI 环境不应启用 XVFB");
        assertTrue(matrix(name -> "CI".equals(name) ? "true" : null).useXvfb());
        assertTrue(matrix(name -> "GITHUB_ACTIONS".equals(name) ? "true" : null).useXvfb());
        assertFalse(matrix(name -> "CI".equals(name) ? "false" : null).useXvfb(), "显式 false 不算 CI");
    }

    private static ClientTestMatrix matrix(ClientTestMatrix.EnvLookup env) {
        return new ClientTestMatrix(List.of("1.21.1"), List.of("fabric"), List.of("21"), List.of("linux"), env);
    }
}
