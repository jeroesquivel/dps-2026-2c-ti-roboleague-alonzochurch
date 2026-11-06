package com.dps.roboleague.appeals.application;

import com.dps.roboleague.appeals.Appeal;
import com.dps.roboleague.appeals.AppealDecision;
import com.dps.roboleague.appeals.AppealRepository;
import com.dps.roboleague.appeals.port.in.AcceptAppeal;
import com.dps.roboleague.results.application.ResultCorrector;
import com.dps.roboleague.results.result.AcceptedAppeal;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public final class AcceptAppealUseCase implements AcceptAppeal {

    private final AppealRepository appeals;
    private final RunResultRepository runResults;
    private final ResultCorrector corrector;
    private final AuditLog auditLog;
    private final Clock clock;

    public AcceptAppealUseCase(AppealRepository appeals, RunResultRepository runResults, ResultCorrector corrector,
            AuditLog auditLog, Clock clock) {
        this.appeals = appeals;
        this.runResults = runResults;
        this.corrector = corrector;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Appeal execute(Command command) {
        Appeal appeal = appeals.findById(command.appealId())
                .orElseThrow(() -> NotFoundException.of("Appeal", command.appealId().value()));
        RunResult run = runResults.findById(appeal.runId())
                .orElseThrow(() -> NotFoundException.of("RunResult", appeal.runId().value()));

        Instant now = clock.instant();
        AcceptedAppeal origin = new AcceptedAppeal(appeal.id());
        Appeal accepted = appeal.accept(new AppealDecision(command.reviewer(), command.rationale(), now));
        RunResult corrected = corrector.correct(run, command.amendment(), origin, origin.description(),
                command.reviewer());

        appeals.save(accepted);
        auditLog.record(new AuditEvent(now, AuditAction.APPEAL_RESOLVED, accepted.id(), command.reviewer(),
                Map.of(AuditDetail.STATUS, accepted.status().name(), AuditDetail.RATIONALE, command.rationale())));
        corrector.apply(corrected, origin, origin.description(), command.reviewer());
        return accepted;
    }
}
