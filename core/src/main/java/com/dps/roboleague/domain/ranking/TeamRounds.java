package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.shared.ChallengeId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record TeamRounds(List<PlayedRound> rounds) {

    public TeamRounds {
        rounds = rounds.stream().sorted(Comparator.comparing(PlayedRound::ordinal)).toList();
    }

    public TeamRuns runs(AttemptAggregation aggregation) {
        return new TeamRuns(PlayedRound.attemptsOf(rounds), aggregation);
    }

    public ScoreExplanation explain(AttemptAggregation aggregation, Function<ChallengeId, RoundSelection> selectionOf) {
        Map<ChallengeId, List<PlayedRound>> byChallenge = rounds.stream()
                .collect(Collectors.groupingBy(PlayedRound::challengeId, LinkedHashMap::new, Collectors.toList()));
        ExplanationDraft draft = new ExplanationDraft(aggregation);
        byChallenge.forEach((challengeId, played) ->
                selectionOf.apply(challengeId).explain(new ChallengeRounds(challengeId, played), draft));
        return draft.explanation();
    }
}
