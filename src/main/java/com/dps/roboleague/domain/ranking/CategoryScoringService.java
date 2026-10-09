package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.shared.CategoryId;
import com.dps.roboleague.domain.shared.CompetitionId;
import com.dps.roboleague.domain.shared.NotFoundException;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CategoryScoringService {

    private final RoundRepository rounds;
    private final RunResultRepository runResults;
    private final RulebookRepository rulebooks;
    private final RankingService rankingService;

    public CategoryScoringService(RoundRepository rounds, RunResultRepository runResults,
            RulebookRepository rulebooks, RankingService rankingService) {
        this.rounds = rounds;
        this.runResults = runResults;
        this.rulebooks = rulebooks;
        this.rankingService = rankingService;
    }

    public List<StandingEntry> rank(CompetitionId competitionId, CategoryId categoryId, Rulebook rulebook) {
        return rankingService.rank(collect(competitionId, categoryId, rulebook.attemptAggregation()),
                rulebook.tiebreakRules());
    }

    public List<TeamScoreSummary> collect(CompetitionId competitionId, CategoryId categoryId,
            AttemptAggregation aggregation) {
        Map<TeamId, List<ScoredRun>> runsByTeam = new LinkedHashMap<>();
        for (RunResult run : runsOf(competitionId, categoryId)) {
            runsByTeam.computeIfAbsent(run.teamId(), team -> new ArrayList<>()).add(scoreRun(run, competitionId));
        }
        return runsByTeam.entrySet().stream()
                .map(entry -> new TeamScoreSummary(entry.getKey(), new TeamRuns(entry.getValue(), aggregation)))
                .toList();
    }

    public List<RunResult> runsOf(CompetitionId competitionId, CategoryId categoryId) {
        List<RunResult> runs = new ArrayList<>();
        for (Round round : rounds.findByCategory(competitionId, categoryId)) {
            runs.addAll(runResults.findByRound(round.id()));
        }
        return List.copyOf(runs);
    }

    public ScoredRun scoreRun(RunResult run, CompetitionId competitionId) {
        Rulebook rulebook = rulebooks.find(competitionId, run.rulebookVersion())
                .orElseThrow(() -> NotFoundException.of("Rulebook", run.rulebookVersion().toString()));
        return new ScoredRun(run.id(), run.challengeId(), run.currentMeasurements(),
                rulebook.challenge(run.challengeId()).score(run.scoringContext()));
    }
}
