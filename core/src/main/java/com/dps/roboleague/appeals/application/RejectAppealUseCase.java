package com.dps.roboleague.appeals.application;

import com.dps.roboleague.appeals.Appeal;
import com.dps.roboleague.appeals.AppealDecision;
import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.appeals.port.in.RejectAppeal;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.util.Map;

public final class RejectAppealUseCase implements RejectAppeal {

    private final AppealRepository appeals;
    private final AuditLog auditLog;
    private final Clock clock;

    public RejectAppealUseCase(AppealRepository appeals, AuditLog auditLog, Clock clock) {
        this.appeals = appeals;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Appeal execute(Command command) {
        Appeal appeal = appeals.findById(command.appealId())
                .orElseThrow(() -> NotFoundException.of("Appeal", command.appealId().value()));
        Appeal rejected = appeal.reject(new AppealDecision(command.reviewer(), command.rationale(), clock.instant()));

        appeals.save(rejected);
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.APPEAL_RESOLVED, rejected.id(),
                command.reviewer(),
                Map.of(AuditDetail.STATUS, rejected.status().name(), AuditDetail.RATIONALE, command.rationale())));
        return rejected;
    }
}
