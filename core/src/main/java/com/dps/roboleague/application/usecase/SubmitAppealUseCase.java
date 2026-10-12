package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.appeal.AppealWindow;
import com.dps.roboleague.domain.appeal.Appeals;
import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.shared.NotFoundException;
import java.time.Clock;
import java.util.Map;

public final class SubmitAppealUseCase implements SubmitAppeal {

    private final RunResultRepository runResults;
    private final RoundRepository rounds;
    private final RulebookRepository rulebooks;
    private final AppealRepository appeals;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;
    private final Clock clock;

    public SubmitAppealUseCase(RunResultRepository runResults, RoundRepository rounds, RulebookRepository rulebooks,
            AppealRepository appeals, IdGenerator idGenerator, AuditLog auditLog, Clock clock) {
        this.runResults = runResults;
        this.rounds = rounds;
        this.rulebooks = rulebooks;
        this.appeals = appeals;
        this.idGenerator = idGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public AppealId execute(Command command) {
        RunResult run = runResults.findById(command.runId())
                .orElseThrow(() -> NotFoundException.of("RunResult", command.runId().value()));
        Round round = rounds.findById(run.roundId())
                .orElseThrow(() -> NotFoundException.of("Round", run.roundId().value()));
        AppealWindow window = rulebooks.find(round.competitionId(), run.rulebookVersion())
                .map(Rulebook::appealWindow)
                .orElseThrow(() -> NotFoundException.of("Rulebook", run.rulebookVersion().toString()));
        new Appeals(appeals.findByRun(run.id())).requireNoneOn(run.id());

        Appeal appeal = Appeal.file(idGenerator.nextAppealId(), run, command.teamId(), command.claim(),
                clock.instant(), window);
        appeals.save(appeal);
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.APPEAL_SUBMITTED, appeal.id(),
                command.actor(), Map.of(AuditDetail.RUN, run.id().value())));
        return appeal.id();
    }
}
