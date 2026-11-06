package com.dps.roboleague.rules.ranking.rule;

import com.dps.roboleague.rules.ranking.TeamScoreSummary;
import com.dps.roboleague.rules.ranking.TiebreakRule;
import com.dps.roboleague.shared.Points;

public final class HighestSingleRunTiebreak implements TiebreakRule {

    public static final String CODE = "HIGHEST_SINGLE_RUN";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String description() {
        return "the team with the highest single run ranks first";
    }

    @Override
    public int compare(TeamScoreSummary left, TeamScoreSummary right) {
        Points leftBest = left.bestRunPoints().orElse(Points.ZERO);
        Points rightBest = right.bestRunPoints().orElse(Points.ZERO);
        return rightBest.compareTo(leftBest);
    }
}
