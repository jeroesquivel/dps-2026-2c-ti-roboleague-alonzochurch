package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.RecalculateStandings;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.result.CorrectionOrigin;
import com.dps.roboleague.results.result.ResultAmendment;
import com.dps.roboleague.results.result.ResultCorrection;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.rules.challenge.ChallengeSpec;
import com.dps.roboleague.rules.rulebook.RulebookRepository;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.NotFoundException;
import com.dps.roboleague.shared.audit.AuditAction;
import com.dps.roboleague.shared.audit.AuditDetail;
import com.dps.roboleague.shared.audit.AuditEvent;
import com.dps.roboleague.shared.audit.AuditLog;
import java.time.Clock;
import java.util.Map;

/**
 * Lo que comparten todas las formas de corregir un resultado, sea por una apelación aceptada o por un error de
 * carga: validar la enmienda contra el reglamento con que se jugó, guardar la corrida corregida, dejar la auditoría
 * y recalcular la tabla si ya existe.
 */
public final class ResultCorrector {

    private final RoundRepository rounds;
    private final RulebookRepository rulebooks;
    private final RunResultRepository runResults;
    private final StandingsRepository standings;
    private final RecalculateStandings recalculateStandings;
    private final AuditLog auditLog;
    private final Clock clock;

    public ResultCorrector(RoundRepository rounds, RulebookRepository rulebooks, RunResultRepository runResults,
            StandingsRepository standings, RecalculateStandings recalculateStandings, AuditLog auditLog,
            Clock clock) {
        this.rounds = rounds;
        this.rulebooks = rulebooks;
        this.runResults = runResults;
        this.standings = standings;
        this.recalculateStandings = recalculateStandings;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public Round roundOf(RunResult run) {
        return rounds.findById(run.roundId())
                .orElseThrow(() -> NotFoundException.of("Round", run.roundId().value()));
    }

    /** Valida la enmienda y devuelve la corrida corregida, sin guardar nada todavía. */
    public RunResult correct(RunResult run, ResultAmendment amendment, CorrectionOrigin origin, String reason,
            Actor actor) {
        Round round = roundOf(run);
        ChallengeSpec challenge = rulebooks.find(round.competitionId(), run.rulebookVersion())
                .orElseThrow(() -> NotFoundException.of("Rulebook", run.rulebookVersion().toString()))
                .challenge(run.challengeId());
        ResultCorrection correction = new ResultCorrection(clock.instant(), actor, reason,
                amendment.measurements(), amendment.incidents(), amendment.evaluationsOver(run.currentEvaluations()),
                origin);
        return run.applyCorrection(correction, challenge, round);
    }

    /** Guarda la corrida ya corregida, audita el cambio y recalcula la tabla vigente de la categoría. */
    public void apply(RunResult corrected, CorrectionOrigin origin, String reason, Actor actor) {
        Round round = roundOf(corrected);
        runResults.save(corrected);
        auditLog.record(new AuditEvent(clock.instant(), AuditAction.RESULT_CORRECTED, corrected.id(), actor,
                Map.of(AuditDetail.ORIGINAL, corrected.originalMeasurements().toString(),
                        AuditDetail.CORRECTED, corrected.currentMeasurements().toString(),
                        AuditDetail.EVALUATIONS, corrected.currentEvaluations().toString(),
                        AuditDetail.ORIGIN, origin.description(),
                        AuditDetail.REASON, reason)));
        standings.findLatest(round.competitionId(), round.categoryId())
                .ifPresent(current -> recalculateStandings.execute(new RecalculateStandings.Command(
                        round.competitionId(), round.categoryId(), reason, actor)));
    }
}
