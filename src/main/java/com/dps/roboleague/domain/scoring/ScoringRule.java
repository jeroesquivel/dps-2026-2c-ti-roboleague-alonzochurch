package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.challenge.MetricKey;
import java.util.List;
import java.util.Set;

public interface ScoringRule {

    List<ScoreContribution> apply(ScoringContext context);

    Set<MetricKey> referencedMetrics();
}
