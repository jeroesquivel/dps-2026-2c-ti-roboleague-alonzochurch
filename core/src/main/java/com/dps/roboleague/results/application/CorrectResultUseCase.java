package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.CorrectResult;
import com.dps.roboleague.results.ranking.StandingsRepository;
import com.dps.roboleague.results.result.DataEntryError;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.shared.ConflictException;
import com.dps.roboleague.shared.NotFoundException;

/**
 * Corrige un resultado mal cargado, sea una medición, un incidente o la evaluación de un juez. Solo mientras la tabla
 * de la categoría no es final: una vez publicada, el único camino para cambiar un resultado es una apelación.
 */
public final class CorrectResultUseCase implements CorrectResult {

    private final RunResultRepository runResults;
    private final StandingsRepository standings;
    private final ResultCorrector corrector;

    public CorrectResultUseCase(RunResultRepository runResults, StandingsRepository standings,
            ResultCorrector corrector) {
        this.runResults = runResults;
        this.standings = standings;
        this.corrector = corrector;
    }

    @Override
    public RunResult execute(Command command) {
        RunResult run = runResults.findById(command.runId())
                .orElseThrow(() -> NotFoundException.of("RunResult", command.runId().value()));
        Round round = corrector.roundOf(run);
        standings.findLatest(round.competitionId(), round.categoryId())
                .filter(current -> current.isFinal())
                .ifPresent(current -> {
                    throw new ConflictException("the standings of category " + round.categoryId().value()
                            + " are final: result " + run.id().value() + " can only be changed by an appeal");
                });

        DataEntryError origin = new DataEntryError();
        RunResult corrected = corrector.correct(run, command.amendment(), origin, command.reason(),
                command.actor());
        corrector.apply(corrected, origin, command.reason(), command.actor());
        return corrected;
    }
}
