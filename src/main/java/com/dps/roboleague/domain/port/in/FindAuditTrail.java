package com.dps.roboleague.domain.port.in;

import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.shared.Identifier;
import java.util.List;

public interface FindAuditTrail {

    List<AuditEvent> execute(Command command);

    record Command(Identifier subject) {
    }
}
