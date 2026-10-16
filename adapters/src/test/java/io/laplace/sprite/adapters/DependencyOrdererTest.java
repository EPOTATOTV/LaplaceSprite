package io.laplace.sprite.adapters;

import io.laplace.sprite.adapters.deps.DependencyOrderer;
import io.laplace.sprite.core.model.Artifact;
import io.laplace.sprite.core.model.ArtifactDependency;
import io.laplace.sprite.core.model.ArtifactRole;
import io.laplace.sprite.core.model.ArtifactSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 依赖三语义排序（14.2）。 */
class DependencyOrdererTest {

    private final DependencyOrderer orderer = new DependencyOrderer();

    @Test
    void ordersHardDependenciesBeforeDependents() {
        Artifact c = artifact("c");
        Artifact b = artifact("b", List.of(ArtifactDependency.hard("c")));
        Artifact a = artifact("a", List.of(ArtifactDependency.hard("b")));
        assertEquals(List.of("c", "b", "a"), orderer.orderIds(List.of(a, b, c)));
    }

    @Test
    void softDependenciesOnlyAffectOrdering() {
        Artifact c = artifact("c");
        Artifact a = artifact("a", List.of(ArtifactDependency.hard("b")));
        Artifact b = artifact("b", List.of(ArtifactDependency.hard("c")));
        Artifact d = artifact("d", List.of(ArtifactDependency.soft("c")));
        List<String> ids = orderer.orderIds(List.of(a, b, c, d));
        assertTrue(ids.indexOf("c") < ids.indexOf("b"), ids.toString());
        assertTrue(ids.indexOf("b") < ids.indexOf("a"), ids.toString());
        assertTrue(ids.indexOf("c") < ids.indexOf("d"), ids.toString());
    }

    @Test
    void reverseSemanticsInvertTheEdge() {
        Artifact e = artifact("e", List.of(ArtifactDependency.reverse("b")));
        Artifact b = artifact("b", List.of(ArtifactDependency.hard("c")));
        Artifact c = artifact("c");
        List<String> ids = orderer.orderIds(List.of(b, c, e));
        assertTrue(ids.indexOf("e") < ids.indexOf("b"), ids.toString());
    }

    @Test
    void softDependencyOnMissingArtifactIsSilentlyDowngraded() {
        Artifact a = artifact("a", List.of(ArtifactDependency.soft("ghost")));
        assertEquals(List.of("a"), orderer.orderIds(List.of(a)));
        assertTrue(orderer.verify(List.of(a)).stream().anyMatch(notice -> notice.contains("软依赖降级")));
    }

    @Test
    void missingHardDependencyFails() {
        Artifact a = artifact("a", List.of(ArtifactDependency.hard("missing-dep")));
        DependencyOrderer.MissingHardDependencyException error = assertThrows(
                DependencyOrderer.MissingHardDependencyException.class, () -> orderer.order(List.of(a)));
        assertTrue(error.getMessage().contains("missing-dep"), error.getMessage());
        assertTrue(orderer.verify(List.of(a)).stream().anyMatch(notice -> notice.contains("硬依赖缺失")));
    }

    @Test
    void cycleDetectionFails() {
        Artifact x = artifact("x", List.of(ArtifactDependency.hard("y")));
        Artifact y = artifact("y", List.of(ArtifactDependency.hard("x")));
        DependencyOrderer.DependencyCycleException error = assertThrows(
                DependencyOrderer.DependencyCycleException.class, () -> orderer.order(List.of(x, y)));
        assertTrue(error.getMessage().contains("环"), error.getMessage());
        assertTrue(orderer.verify(List.of(x, y)).stream().anyMatch(notice -> notice.contains("排序失败")));
    }

    @Test
    void orderingIsDeterministic() {
        List<Artifact> artifacts = List.of(artifact("b"), artifact("a"), artifact("c"));
        assertEquals(orderer.orderIds(artifacts), orderer.orderIds(List.of(artifact("c"), artifact("b"),
                artifact("a"))));
    }

    private static Artifact artifact(String id) {
        return artifact(id, List.of());
    }

    private static Artifact artifact(String id, List<ArtifactDependency> dependencies) {
        return new Artifact(id, id, "1.0.0", ArtifactSource.MAVEN, "io.laplace.test:" + id + ":1.0.0", null,
                ArtifactRole.PLUGIN, dependencies, Map.of());
    }
}
