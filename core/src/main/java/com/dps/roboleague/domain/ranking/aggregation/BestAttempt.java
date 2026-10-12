package com.dps.roboleague.domain.ranking.aggregation;

import com.dps.roboleague.domain.ranking.AttemptAggregation;
import com.dps.roboleague.domain.ranking.ScoredRun;
import com.dps.roboleague.domain.shared.Points;
import java.util.Comparator;
import java.util.List;

public final class BestAttempt implements AttemptAggregation {

    public static final String CODE = "BEST_ATTEMPT";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String description() {
        return "only the highest scoring attempt of the team counts";
    }

    @Override
    public Points aggregate(List<ScoredRun> attempts) {
        return attempts.stream().map(ScoredRun::total).max(Comparator.naturalOrder()).orElse(Points.ZERO);
    }
}