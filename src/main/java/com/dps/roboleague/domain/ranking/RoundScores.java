package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.challenge.BestRounds;
import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

public record RoundScores(List<RoundScore> scores) {

    private static final Comparator<RoundScore> BEST_FIRST = Comparator.comparing(RoundScore::points).reversed()
            .thenComparing(RoundScore::ordinal);

    public RoundScores {
        scores = List.copyOf(scores);
        Set<RoundOrdinal> seen = new HashSet<>();
        scores.stream()
                .map(RoundScore::ordinal)
                .filter(ordinal -> !seen.add(ordinal))
                .findFirst()
                .ifPresent(repeated -> {
                    throw new InvalidValueException("round " + repeated.value() + " is scored twice for the team");
                });
    }

    public ScoreSubtotal bestOf(BestRounds rule, ChallengeId challengeId) {
        List<RoundScore> ranked = scores.stream().sorted(BEST_FIRST).toList();
        int counted = rule.countedOutOf(ranked.size());
        List<RoundOutcome> outcomes = IntStream.range(0, ranked.size())
                .mapToObj(index -> index < counted
                        ? RoundOutcome.counted(ranked.get(index), "among the best " + rule.counted())
                        : RoundOutcome.discarded(ranked.get(index),
                                discardReason(ranked.get(index), ranked.get(counted - 1), rule)))
                .sorted(Comparator.comparing(RoundOutcome::ordinal))
                .toList();
        return ScoreSubtotal.ofRounds(BestRounds.CODE,
                rule.description() + " of challenge " + challengeId.value(), outcomes);
    }

    private static String discardReason(RoundScore discarded, RoundScore lastCounted, BestRounds rule) {
        return discarded.points().compareTo(lastCounted.points()) == 0
                ? "tied with round " + lastCounted.ordinal().value() + ", the lower ordinal wins"
                : "outside the best " + rule.counted();
    }
}
