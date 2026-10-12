package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.TeamId;
import java.util.List;
import java.util.Objects;

public record StandingEntry(int position, TeamId teamId, Points totalPoints,
        List<AppliedTiebreak> appliedTiebreaks, ScoreExplanation explanation) {

    public StandingEntry {
        Objects.requireNonNull(teamId, "team id is required");
        Objects.requireNonNull(totalPoints, "total points are required");
        Objects.requireNonNull(explanation, "score explanation is required");
        if (position < 1) {
            throw new InvalidValueException("a standing position must be positive");
        }
        appliedTiebreaks = List.copyOf(appliedTiebreaks);
        if (explanation.total().compareTo(totalPoints) != 0) {
            throw new InvalidValueException("the explanation of team " + teamId.value() + " adds up "
                    + explanation.total() + " but its total is " + totalPoints);
        }
    }

    public StandingEntry(int position, TeamId teamId, Points totalPoints, List<AppliedTiebreak> appliedTiebreaks) {
        this(position, teamId, totalPoints, appliedTiebreaks, ScoreExplanation.stated(totalPoints));
    }
}
