package com.dps.roboleague.rules.scoring;

import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.shared.JudgeId;
import java.util.Objects;

public record JudgeEvaluation(JudgeId judge, MetricKey criterion, JudgeScore score) {

    public JudgeEvaluation {
        Objects.requireNonNull(judge, "judge is required");
        Objects.requireNonNull(criterion, "criterion is required");
        Objects.requireNonNull(score, "score is required");
    }
}
