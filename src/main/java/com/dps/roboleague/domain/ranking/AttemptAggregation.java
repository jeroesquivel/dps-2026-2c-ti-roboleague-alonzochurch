package com.dps.roboleague.domain.ranking;

import com.dps.roboleague.domain.shared.Points;
import java.util.List;

public interface AttemptAggregation {

    String code();

    String description();

    Points aggregate(List<ScoredRun> attempts);
}