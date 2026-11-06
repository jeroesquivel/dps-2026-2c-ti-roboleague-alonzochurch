package com.dps.roboleague.shared.port.in;

import com.dps.roboleague.shared.Identifier;
import com.dps.roboleague.shared.audit.AuditEvent;
import java.util.List;

public interface FindAuditTrail {

    List<AuditEvent> execute(Command command);

    record Command(Identifier subject) {
    }
}
