package com.dps.roboleague.results.application;

import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.ranking.CategoryScoringService;
import com.dps.roboleague.results.result.RunResult;
import com.dps.roboleague.results.result.RunResultRepository;
import com.dps.roboleague.rules.ranking.ScoredRun;
import com.dps.roboleague.scheduling.Round;
import com.dps.roboleague.scheduling.RoundRepository;
import com.dps.roboleague.shared.NotFoundException;

public final class CalculateRunScoreUseCase implements CalculateRunScore {

    private final RunResultRepository runResults;
    private final RoundRepository rounds;
    private final CategoryScoringService scoringService;

    public CalculateRunScoreUseCase(RunResultRepository runResults, RoundRepository rounds,
            CategoryScoringService scoringService) {
        this.runResults = runResults;
        this.rounds = rounds;
        this.scoringService = scoringService;
    }

    @Override
    public RunScore execute(Command command) {
        RunResult run = runResults.findById(command.runId())
                .orElseThrow(() -> NotFoundException.of("RunResult", command.runId().value()));
        Round round = rounds.findById(run.roundId())
                .orElseThrow(() -> NotFoundException.of("Round", run.roundId().value()));
        ScoredRun scored = scoringService.scoreRun(run, round.competitionId());
        return new RunScore(run.id(), run.teamId(), run.challengeId(), run.rulebookVersion(), scored.breakdown(),
                run.completion(), scoringService.scoreBySource(run, round.competitionId()),
                scoringService.bonusCapOutcome(run, round.competitionId()));
    }
}
