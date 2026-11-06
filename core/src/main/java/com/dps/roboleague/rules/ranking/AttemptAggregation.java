package com.dps.roboleague.rules.ranking;

import com.dps.roboleague.shared.Points;
import java.util.List;

public interface AttemptAggregation {

    String code();

    String description();

    Points aggregate(List<ScoredRun> attempts);
}