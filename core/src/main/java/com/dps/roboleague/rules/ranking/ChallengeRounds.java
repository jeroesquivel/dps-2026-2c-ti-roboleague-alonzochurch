package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.ChallengeId;
import java.util.List;
import java.util.Objects;

/** Las rondas que un equipo jugó de un mismo desafío bajo una misma política de reglamento. */
public record ChallengeRounds(ChallengeId challengeId, RoundPolicy policy, List<PlayedRound> played) {

    public ChallengeRounds {
        Objects.requireNonNull(challengeId, "challenge id is required");
        Objects.requireNonNull(policy, "round policy is required");
        played = List.copyOf(played);
    }

    public List<ScoredRun> attempts() {
        return PlayedRound.attemptsOf(played);
    }

    public RoundScores scores() {
        return new RoundScores(played.stream().map(PlayedRound::score).toList());
    }
}
