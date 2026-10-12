package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.util.List;
import java.util.Objects;

public record ScoreSubtotal(String code, String description, List<RoundOutcome> rounds, Points points) {

    public ScoreSubtotal {
        Objects.requireNonNull(points, "subtotal points are required");
        if (code == null || code.isBlank() || description == null || description.isBlank()) {
            throw new InvalidValueException("a score subtotal requires a code and a description");
        }
        rounds = List.copyOf(rounds);
        if (!rounds.isEmpty() && contributionOf(rounds).compareTo(points) != 0) {
            throw new InvalidValueException("subtotal " + code + " must add up the rounds it counts");
        }
    }

    public static ScoreSubtotal of(String code, String description, Points points) {
        return new ScoreSubtotal(code, description, List.of(), points);
    }

    public static ScoreSubtotal ofRounds(String code, String description, List<RoundOutcome> rounds) {
        return new ScoreSubtotal(code, description, rounds, contributionOf(rounds));
    }

    private static Points contributionOf(List<RoundOutcome> rounds) {
        return rounds.stream().map(RoundOutcome::contribution).reduce(Points.ZERO, Points::plus);
    }
}
