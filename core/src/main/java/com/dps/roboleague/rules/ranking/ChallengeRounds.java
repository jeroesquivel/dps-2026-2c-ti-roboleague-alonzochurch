package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.ChallengeId;
import com.dps.roboleague.shared.RulebookVersion;
import java.util.List;
import java.util.Objects;

/** Las rondas que un equipo jugó de un mismo desafío bajo una misma política de reglamento. */
public record ChallengeRounds(ChallengeId challengeId, List<PlayedRound> played) {

    public ChallengeRounds {
        Objects.requireNonNull(challengeId, "challenge id is required");
        played = List.copyOf(played);
    }

    public List<RulebookVersion> versions() {
        return played.stream().map(round -> round.policy().version()).distinct().toList();
    }

    public RoundScores scores() {
        return new RoundScores(played.stream().map(PlayedRound::score).toList());
    }
}
