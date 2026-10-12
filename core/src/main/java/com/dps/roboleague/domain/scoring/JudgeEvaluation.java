package com.dps.roboleague.domain.scoring;

import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.shared.JudgeId;
import java.util.Objects;

public record JudgeEvaluation(JudgeId judge, MetricKey criterion, JudgeScore score) {

    public JudgeEvaluation {
        Objects.requireNonNull(judge, "judge is required");
        Objects.requireNonNull(criterion, "criterion is required");
        Objects.requireNonNull(score, "score is required");
    }
}
