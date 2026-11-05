package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.shared.ChallengeId;
import java.util.List;
import java.util.Objects;

/** Las rondas que un equipo jugó de un mismo desafío. */
public record ChallengeRounds(ChallengeId challengeId, List<PlayedRound> played) {

    public ChallengeRounds {
        Objects.requireNonNull(challengeId, "challenge id is required");
        played = List.copyOf(played);
    }

    public List<ScoredRun> attempts() {
        return PlayedRound.attemptsOf(played);
    }

    public RoundScores scores(AttemptAggregation aggregation) {
        return new RoundScores(played.stream().map(round -> round.score(aggregation)).toList());
    }
}
