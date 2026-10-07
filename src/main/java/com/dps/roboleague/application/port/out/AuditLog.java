package com.dps.roboleague.application.port.out;

import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.shared.Identifier;
import java.util.List;

public interface AuditLog {

    void record(AuditEvent event);

    List<AuditEvent> findBySubject(Identifier subject);
}
