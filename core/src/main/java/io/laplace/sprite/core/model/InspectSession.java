package io.laplace.sprite.core.model;

import java.time.Instant;
import java.util.List;

/**
 * 查端会话（11.4 查端会话面板）。
 */
public record InspectSession(String sessionId, String playerId, String simId, List<String> relatedFlags,
                             InspectStatus status, List<String> evidenceRefs, Instant openedAt) {

    public InspectSession {
        relatedFlags = relatedFlags == null ? List.of() : List.copyOf(relatedFlags);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }

    public InspectSession resolve(InspectStatus newStatus) {
        return new InspectSession(sessionId, playerId, simId, relatedFlags, newStatus, evidenceRefs, openedAt);
    }
}
