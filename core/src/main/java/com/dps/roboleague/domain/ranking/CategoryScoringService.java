package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.SourcedScore;
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
import java.util.Optional;

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
        return rankingService.rank(collect(competitionId, categoryId, rulebook), rulebook.tiebreakRules());
    }

    public List<TeamScoreSummary> collect(CompetitionId competitionId, CategoryId categoryId, Rulebook rulebook) {
        Map<TeamId, List<PlayedRound>> roundsByTeam = new LinkedHashMap<>();
        for (Round round : rounds.findByCategory(competitionId, categoryId)) {
            Map<TeamId, List<ScoredRun>> attemptsByTeam = new LinkedHashMap<>();
            runResults.findByRound(round.id()).stream()
                    .filter(run -> run.completion().isComplete())
                    .forEach(run -> attemptsByTeam.computeIfAbsent(run.teamId(), team -> new ArrayList<>())
                            .add(scoreRun(run, competitionId)));
            attemptsByTeam.forEach((team, attempts) -> roundsByTeam.computeIfAbsent(team, key -> new ArrayList<>())
                    .add(new PlayedRound(round.ordinal(), round.challengeId(), attempts)));
        }
        AttemptAggregation aggregation = rulebook.attemptAggregation();
        return roundsByTeam.entrySet().stream()
                .map(entry -> summaryOf(entry.getKey(), new TeamRounds(entry.getValue()), aggregation, rulebook))
                .toList();
    }

    public List<RunResult> runsOf(CompetitionId competitionId, CategoryId categoryId) {
        List<RunResult> runs = new ArrayList<>();
        for (Round round : rounds.findByCategory(competitionId, categoryId)) {
            runs.addAll(runResults.findByRound(round.id()));
        }
        return List.copyOf(runs);
    }

    public PendingRuns pendingRuns(CompetitionId competitionId, CategoryId categoryId) {
        return PendingRuns.among(runsOf(competitionId, categoryId));
    }

    public ScoredRun scoreRun(RunResult run, CompetitionId competitionId) {
        return new ScoredRun(run.id(), run.challengeId(), run.currentMeasurements(),
                challengeOf(run, competitionId).score(run.scoringContext()));
    }

    public Optional<SourcedScore> scoreBySource(RunResult run, CompetitionId competitionId) {
        return challengeOf(run, competitionId).scoreBySource(run.scoringContext());
    }

    private ChallengeSpec challengeOf(RunResult run, CompetitionId competitionId) {
        return rulebooks.find(competitionId, run.rulebookVersion())
                .orElseThrow(() -> NotFoundException.of("Rulebook", run.rulebookVersion().toString()))
                .challenge(run.challengeId());
    }

    private static TeamScoreSummary summaryOf(TeamId teamId, TeamRounds teamRounds, AttemptAggregation aggregation,
            Rulebook rulebook) {
        return new TeamScoreSummary(teamId, teamRounds.runs(aggregation),
                teamRounds.explain(aggregation, rulebook::bestRoundsOf));
    }
}
