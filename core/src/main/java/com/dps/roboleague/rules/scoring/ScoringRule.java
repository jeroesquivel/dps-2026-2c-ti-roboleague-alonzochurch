package com.dps.roboleague.rules.scoring;

import com.dps.roboleague.rules.challenge.MetricKey;
import java.util.List;
import java.util.Set;

public interface ScoringRule {

    List<ScoreContribution> apply(ScoringContext context);

    Set<MetricKey> referencedMetrics();
}
