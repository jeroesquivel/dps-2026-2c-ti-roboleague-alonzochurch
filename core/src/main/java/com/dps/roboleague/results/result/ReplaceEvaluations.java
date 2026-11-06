package com.dps.roboleague.results.result;

import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import java.util.Objects;

/** La corrección trae el conjunto completo de evaluaciones que reemplaza a las vigentes. */
public record ReplaceEvaluations(JudgeEvaluations evaluations) implements EvaluationsChange {

    public ReplaceEvaluations {
        Objects.requireNonNull(evaluations, "corrected evaluations are required");
    }

    @Override
    public JudgeEvaluations over(JudgeEvaluations current) {
        return evaluations;
    }
}
