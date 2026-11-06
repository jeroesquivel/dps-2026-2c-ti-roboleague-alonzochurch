package com.dps.roboleague.shared.audit;

import com.dps.roboleague.shared.Identifier;
import java.util.List;

public interface AuditLog {

    void record(AuditEvent event);

    List<AuditEvent> findBySubject(Identifier subject);
}
