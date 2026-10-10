package com.dps.roboleague.application.usecase;

import com.dps.roboleague.domain.audit.AuditAction;
import com.dps.roboleague.domain.audit.AuditDetail;
import com.dps.roboleague.domain.audit.AuditEvent;
import com.dps.roboleague.domain.audit.AuditLog;
import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.port.in.RegisterPanelEvaluations;
import com.dps.roboleague.domain.ranking.StandingsHistory;
import com.dps.roboleague.domain.ranking.StandingsRepository;
import com.dps.roboleague.domain.result.PanelSubmission;
import com.dps.roboleague.domain.result.RoundResults;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.shared.IdGenerator;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.RunId;
import java.time.Clock;
import java.util.Map;

public final class RegisterPanelEvaluationsUseCase implements RegisterPanelEvaluations {

    private final RoundRepository rounds;
    private final RulebookRepository rulebooks;
    private final RunResultRepository runResults;
    private final StandingsRepository standings;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;
    private final Clock clock;

    public RegisterPanelEvaluationsUseCase(RoundRepository rounds, RulebookRepository rulebooks,
            RunResultRepository runResults, StandingsRepository standings, IdGenerator idGenerator,
            AuditLog auditLog, Clock clock) {
        this.rounds = rounds;
        this.rulebooks = rulebooks;
        this.runResults = runResults;
        this.standings = standings;
        this.idGenerator = idGenerator;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    @Override
    public RunId execute(Command command) {
        Round round = rounds.findById(command.roundId())
                .orElseThrow(() -> NotFoundException.of("Round", command.roundId().value()));
        ChallengeSpec challenge = rulebooks.find(round.competitionId(), round.rulebookVersion())
                .orElseThrow(() -> NotFoundException.of("Rulebook", round.rulebookVersion().toString()))
                .challenge(round.challengeId());
        new StandingsHistory(round.competitionId(), round.categoryId(),
                standings.findHistory(round.competitionId(), round.categoryId())).requireOpenForResults();

        PanelSubmission submission = new PanelSubmission(command.evaluations(), command.incidents(),
                command.actor(), clock.instant());
        RunResult result = new RoundResults(runResults.findByRound(round.id())).receive(idGenerator::nextRunId,
                round, command.teamId(), command.attemptNumber(), challenge, submission);
        runResults.save(result);

        auditLog.record(new AuditEvent(submission.receivedAt(), AuditAction.RESULT_SOURCE_RECEIVED, result.id(),
                command.actor(), Map.of(AuditDetail.SOURCE, submission.receipt().source().name(),
                        AuditDetail.EVALUATIONS, command.evaluations().toString(),
                        AuditDetail.INCIDENTS, submission.incidents().toString(),
                        AuditDetail.STATUS, result.completion().status().name(),
                        AuditDetail.RULEBOOK, round.rulebookVersion().toString())));
        return result.id();
    }
}
