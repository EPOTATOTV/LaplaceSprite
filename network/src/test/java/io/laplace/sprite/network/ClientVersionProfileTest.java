package io.laplace.sprite.network;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientVersionProfileTest {

    @Test
    void catalogCoversLegacyToBedrock() {
        Set<String> versions = ClientVersionProfile.catalog().stream()
                .map(ClientVersionProfile::version)
                .collect(Collectors.toSet());
        assertTrue(versions.containsAll(List.of("1.8", "1.12", "1.16", "1.20", "1.21", "bedrock-1.21")),
                "缺少版本: " + versions);
    }

    @Test
    void ofResolvesKnownAndUnknownVersions() {
        assertFalse(ClientVersionProfile.of("1.8").hasElytra());
        assertFalse(ClientVersionProfile.of("1.8").hasAttackCooldown());
        assertTrue(ClientVersionProfile.of("1.12").hasElytra());
        assertTrue(ClientVersionProfile.of("1.21").hasElytra());
        assertTrue(ClientVersionProfile.of("bedrock-1.21").bedrock());
        assertFalse(ClientVersionProfile.of("bedrock-1.21").fluidPushAffectsMovement());
        assertEquals("1.21", ClientVersionProfile.of("1.99").version());
        assertEquals("bedrock-1.21", ClientVersionProfile.of("bedrock-1.30").version());
        assertEquals("1.21", ClientVersionProfile.of(null).version());
    }

    @Test
    void crouchEyeHeightStaysBelowStandingEyeHeight() {
        for (ClientVersionProfile profile : ClientVersionProfile.catalog()) {
            assertTrue(profile.hitboxWidth() > 0, profile.version() + " 命中体积非法");
            assertTrue(profile.crouchEyeHeight() < profile.eyeHeight(), profile.version() + " 视高关系非法");
        }
    }
}
