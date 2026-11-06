package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.RoundOrdinal;
import java.util.List;
import java.util.Objects;

public record PlayedRound(RoundOrdinal ordinal, ChallengeId challengeId, RoundPolicy policy,
        List<ScoredRun> attempts) {

    public PlayedRound {
        Objects.requireNonNull(ordinal, "round ordinal is required");
        Objects.requireNonNull(challengeId, "challenge id is required");
        Objects.requireNonNull(policy, "round policy is required");
        attempts = List.copyOf(attempts);
        if (attempts.isEmpty()) {
            throw new InvalidValueException("round " + ordinal.value() + " requires at least one attempt to count as played");
        }
        if (attempts.stream().anyMatch(attempt -> !attempt.challengeId().equals(challengeId))) {
            throw new InvalidValueException("every attempt of round " + ordinal.value()
                    + " must belong to challenge " + challengeId.value());
        }
    }

    public static List<ScoredRun> attemptsOf(List<PlayedRound> played) {
        return played.stream().flatMap(round -> round.attempts().stream()).toList();
    }

    public RoundScore score() {
        return new RoundScore(ordinal, policy.aggregation().aggregate(attempts));
    }
}
