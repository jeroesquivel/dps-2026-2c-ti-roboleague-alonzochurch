package com.dps.roboleague.results.result;

import com.dps.roboleague.rules.scoring.JudgeEvaluations;

/** La corrección no toca lo que evaluaron los jueces. */
public record KeepEvaluations() implements EvaluationsChange {

    @Override
    public JudgeEvaluations over(JudgeEvaluations current) {
        return current;
    }
}
