package com.dps.roboleague.shared.application;

import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import com.dps.roboleague.shared.port.in.FindAuditTrail;
import java.util.List;

public final class FindAuditTrailUseCase implements FindAuditTrail {

    private final AuditLog auditLog;

    public FindAuditTrailUseCase(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    @Override
    public List<AuditEvent> execute(Command command) {
        return auditLog.findBySubject(command.subject());
    }
}
