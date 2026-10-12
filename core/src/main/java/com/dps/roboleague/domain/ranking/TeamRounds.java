package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.shared.ChallengeId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public record TeamRounds(List<PlayedRound> rounds) {

    public TeamRounds {
        rounds = rounds.stream().sorted(Comparator.comparing(PlayedRound::ordinal)).toList();
    }

    public TeamRuns runs(AttemptAggregation aggregation) {
        return new TeamRuns(attemptsOf(rounds), aggregation);
    }

    public ScoreExplanation explain(AttemptAggregation aggregation,
            Function<ChallengeId, Optional<BestRounds>> bestRoundsOf) {
        Map<ChallengeId, List<PlayedRound>> byChallenge = rounds.stream()
                .collect(Collectors.groupingBy(PlayedRound::challengeId, LinkedHashMap::new, Collectors.toList()));
        List<ScoredRun> pooled = new ArrayList<>();
        List<ScoreSubtotal> selections = new ArrayList<>();
        byChallenge.forEach((challengeId, played) -> bestRoundsOf.apply(challengeId).ifPresentOrElse(
                rule -> selections.add(scoresOf(played, aggregation).bestOf(rule, challengeId)),
                () -> pooled.addAll(attemptsOf(played))));

        List<ScoreSubtotal> subtotals = new ArrayList<>();
        subtotals.add(new TeamRuns(pooled, aggregation).subtotal());
        subtotals.addAll(selections);
        return new ScoreExplanation(subtotals);
    }

    private static RoundScores scoresOf(List<PlayedRound> played, AttemptAggregation aggregation) {
        return new RoundScores(played.stream().map(round -> round.score(aggregation)).toList());
    }

    private static List<ScoredRun> attemptsOf(List<PlayedRound> played) {
        return played.stream().flatMap(round -> round.attempts().stream()).toList();
    }
}
