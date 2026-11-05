package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.SourcedScore;
import com.dps.roboleague.domain.result.RunResult;
import com.dps.roboleague.domain.result.RunResultRepository;
import com.dps.roboleague.domain.rulebook.Rulebook;
import com.dps.roboleague.domain.rulebook.RulebookRepository;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.schedule.Round;
import com.dps.roboleague.domain.schedule.RoundRepository;
import com.dps.roboleague.domain.scoring.BonusCapOutcome;
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
            Map<TeamId, Map<RulebookVersion, List<ScoredRun>>> attemptsByTeam = new LinkedHashMap<>();
            runResults.findByRound(round.id()).stream()
                    .filter(run -> run.completion().isComplete())
                    .forEach(run -> attemptsByTeam.computeIfAbsent(run.teamId(), team -> new LinkedHashMap<>())
                            .computeIfAbsent(run.rulebookVersion(), version -> new ArrayList<>())
                            .add(scoreRun(run, competitionId)));
            attemptsByTeam.forEach((team, byVersion) -> byVersion.forEach((version, attempts) ->
                    roundsByTeam.computeIfAbsent(team, key -> new ArrayList<>()).add(new PlayedRound(
                            round.ordinal(), round.challengeId(),
                            rulebookOf(competitionId, version).roundPolicyOf(round.challengeId()), attempts))));
        }
        return roundsByTeam.entrySet().stream()
                .map(entry -> summaryOf(entry.getKey(), new TeamRounds(entry.getValue()), rulebook))
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

    public Optional<BonusCapOutcome> bonusCapOutcome(RunResult run, CompetitionId competitionId) {
        return challengeOf(run, competitionId).bonusCapOutcome(run.scoringContext());
    }

    private ChallengeSpec challengeOf(RunResult run, CompetitionId competitionId) {
        return rulebookOf(competitionId, run.rulebookVersion()).challenge(run.challengeId());
    }

    private Rulebook rulebookOf(CompetitionId competitionId, RulebookVersion version) {
        return rulebooks.find(competitionId, version)
                .orElseThrow(() -> NotFoundException.of("Rulebook", version.toString()));
    }

    /** El puntaje sale de las políticas de cada ronda; el reglamento vigente solo aporta la vista para desempates. */
    private static TeamScoreSummary summaryOf(TeamId teamId, TeamRounds teamRounds, Rulebook current) {
        return new TeamScoreSummary(teamId, teamRounds.runs(current.attemptAggregation()), teamRounds.explain());
    }
}
