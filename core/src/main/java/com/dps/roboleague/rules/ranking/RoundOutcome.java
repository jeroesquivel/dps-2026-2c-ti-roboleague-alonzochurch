package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RoundOrdinal;
import java.util.Objects;

public record RoundOutcome(RoundOrdinal ordinal, Points points, RoundStatus status, String reason) {

    public RoundOutcome {
        Objects.requireNonNull(ordinal, "round ordinal is required");
        Objects.requireNonNull(points, "round points are required");
        Objects.requireNonNull(status, "round status is required");
        if (reason == null || reason.isBlank()) {
            throw new InvalidValueException("the outcome of round " + ordinal.value() + " requires a reason");
        }
    }

    public static RoundOutcome counted(RoundScore score, String reason) {
        return new RoundOutcome(score.ordinal(), score.points(), RoundStatus.COUNTED, reason);
    }

    public static RoundOutcome discarded(RoundScore score, String reason) {
        return new RoundOutcome(score.ordinal(), score.points(), RoundStatus.DISCARDED, reason);
    }

    public Points contribution() {
        return status.contributionOf(points);
    }
}
