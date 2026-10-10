package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.schedule.RoundOrdinal;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.InvalidValueException;
import java.util.List;
import java.util.Objects;

public record PlayedRound(RoundOrdinal ordinal, ChallengeId challengeId, List<ScoredRun> attempts) {

    public PlayedRound {
        Objects.requireNonNull(ordinal, "round ordinal is required");
        Objects.requireNonNull(challengeId, "challenge id is required");
        attempts = List.copyOf(attempts);
        if (attempts.isEmpty()) {
            throw new InvalidValueException("round " + ordinal.value() + " requires at least one attempt to count as played");
        }
        if (attempts.stream().anyMatch(attempt -> !attempt.challengeId().equals(challengeId))) {
            throw new InvalidValueException("every attempt of round " + ordinal.value()
                    + " must belong to challenge " + challengeId.value());
        }
    }

    public RoundScore score(AttemptAggregation aggregation) {
        return new RoundScore(ordinal, aggregation.aggregate(attempts));
    }
}
