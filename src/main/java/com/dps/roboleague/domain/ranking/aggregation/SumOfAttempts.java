package com.dps.roboleague.domain.ranking.aggregation;

import com.dps.roboleague.domain.ranking.AttemptAggregation;
import com.dps.roboleague.domain.ranking.ScoredRun;
import com.dps.roboleague.domain.shared.Points;
import java.util.List;

public final class SumOfAttempts implements AttemptAggregation {

    public static final String CODE = "SUM_OF_ATTEMPTS";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String description() {
        return "the points of all the attempts of the team are added up";
    }

    @Override
    public Points aggregate(List<ScoredRun> attempts) {
        return attempts.stream().map(ScoredRun::total).reduce(Points.ZERO, Points::plus);
    }
}