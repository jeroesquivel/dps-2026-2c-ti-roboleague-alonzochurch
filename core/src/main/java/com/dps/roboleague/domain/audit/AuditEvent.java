package com.dps.roboleague.domain.audit;

import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.Identifier;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AuditEvent(Instant occurredAt, AuditAction action, Identifier subject, Actor actor,
        Map<AuditDetail, String> details) {

    public AuditEvent {
        Objects.requireNonNull(occurredAt, "audit timestamp is required");
        Objects.requireNonNull(action, "audit action is required");
        Objects.requireNonNull(subject, "audit subject is required");
        Objects.requireNonNull(actor, "audit actor is required");
        details = Map.copyOf(details);
    }

    public static AuditEvent of(Instant occurredAt, AuditAction action, Identifier subject, Actor actor) {
        return new AuditEvent(occurredAt, action, subject, actor, Map.of());
    }
}
