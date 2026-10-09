package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.appeal.Appeal;
import com.dps.roboleague.domain.appeal.AppealDecision;
import com.dps.roboleague.domain.appeal.AppealRepository;
import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.RecalculateStandings;
import com.dps.roboleague.domain.ranking.StandingsRepository;
import com.dps.roboleague.domain.result.ResultCorrection;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.shared.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public final class AcceptAppealUseCase implements AcceptAppeal {

    private final AppealRepository appeals;
    private final RunResultRepository runResults;
    private final RoundRepository rounds;
    private final RulebookRepository rulebooks;
    private final StandingsRepository standings;
    private final RecalculateStandings recalculateStandings;
    private final AuditLog auditLog;
    private final Clock clock;

    public AcceptAppealUseCase(AppealRepository appeals, RunResultRepository runResults, RoundRepository rounds,
            RulebookRepository rulebooks, StandingsRepository standings, RecalculateStandings recalculateStandings,
            AuditLog auditLog, Clock clock) {
        this.appeals = appeals;
        this.runResults = runResults;
        this.rounds = rounds;
        this.rulebooks = rulebooks;
        this.standings = standings;
        this.recalculateStandings = recalculateStandings;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public Appeal execute(Command command) {
        Appeal appeal = appeals.findById(command.appealId())
                .orElseThrow(() -> NotFoundException.of("Appeal", command.appealId().value()));
        RunResult run = runResults.findById(appeal.runId())
                .orElseThrow(() -> NotFoundException.of("RunResult", appeal.runId().value()));
        Round round = rounds.findById(run.roundId())
                .orElseThrow(() -> NotFoundException.of("Round", run.roundId().value()));
        ChallengeSpec challenge = rulebooks.find(round.competitionId(), run.rulebookVersion())
                .orElseThrow(() -> NotFoundException.of("Rulebook", run.rulebookVersion().toString()))
                .challenge(run.challengeId());

        Instant now = clock.instant();
        String reason = "appeal " + appeal.id().value() + " accepted";
        Appeal accepted = appeal.accept(new AppealDecision(command.reviewer(), command.rationale(), now));
        RunResult corrected = run.applyCorrection(new ResultCorrection(now, command.reviewer(), reason,
                command.correctedMeasurements(), command.correctedIncidents(), appeal.id()), challenge);

        appeals.save(accepted);
        runResults.save(corrected);
        auditLog.record(new AuditEvent(now, AuditAction.APPEAL_RESOLVED, accepted.id(), command.reviewer(),
                Map.of(AuditDetail.STATUS, accepted.status().name(), AuditDetail.RATIONALE, command.rationale())));
        auditLog.record(new AuditEvent(now, AuditAction.RESULT_CORRECTED, corrected.id(), command.reviewer(),
                Map.of(AuditDetail.ORIGINAL, corrected.originalMeasurements().toString(),
                        AuditDetail.CORRECTED, corrected.currentMeasurements().toString(),
                        AuditDetail.REASON, reason)));

        standings.findLatest(round.competitionId(), round.categoryId())
                .ifPresent(current -> recalculateStandings.execute(new RecalculateStandings.Command(
                        round.competitionId(), round.categoryId(), reason, command.reviewer())));
        return accepted;
    }
}
