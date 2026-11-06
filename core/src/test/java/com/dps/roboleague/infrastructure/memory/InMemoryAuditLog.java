package com.dps.roboleague.infrastructure.memory;

import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import com.dps.roboleague.shared.Identifier;
import java.util.ArrayList;
import java.util.List;

public final class InMemoryAuditLog implements AuditLog {

    private final List<AuditEvent> events = new ArrayList<>();

    @Override
    public void record(AuditEvent event) {
        events.add(event);
    }

    @Override
    public List<AuditEvent> findBySubject(Identifier subject) {
        return events.stream().filter(event -> event.subject().equals(subject)).toList();
    }
}
