package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.CaptureRunResult;
import com.dps.roboleague.results.ranking.StandingsHistory;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.result.RoundResults;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.shared.IdGenerator;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.util.Map;

public final class CaptureRunResultUseCase implements CaptureRunResult {

    private final RoundRepository rounds;
    private final RulebookRepository rulebooks;
    private final RunResultRepository runResults;
    private final StandingsRepository standings;
    private final IdGenerator idGenerator;
    private final AuditLog auditLog;
    private final Clock clock;

    public CaptureRunResultUseCase(RoundRepository rounds, RulebookRepository rulebooks,
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
        new RoundResults(runResults.findByRound(round.id()))
                .requireUnusedAttempt(command.teamId(), command.attemptNumber());

        RunResult result = RunResult.capture(idGenerator.nextRunId(), round, command.teamId(), challenge,
                command.attemptNumber(), clock.instant(), command.measurements(), command.evaluations(),
                command.incidents());
        runResults.save(result);

        auditLog.record(new AuditEvent(clock.instant(), AuditAction.RESULT_CAPTURED, result.id(), command.actor(),
                Map.of(AuditDetail.MEASUREMENTS, command.measurements().toString(),
                        AuditDetail.RULEBOOK, round.rulebookVersion().toString())));
        return result.id();
    }
}
