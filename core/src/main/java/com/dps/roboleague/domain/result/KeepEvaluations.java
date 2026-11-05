package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.scoring.JudgeEvaluations;

/** La corrección no toca lo que evaluaron los jueces. */
public record KeepEvaluations() implements EvaluationsChange {

    @Override
    public JudgeEvaluations over(JudgeEvaluations current) {
        return current;
    }
}
