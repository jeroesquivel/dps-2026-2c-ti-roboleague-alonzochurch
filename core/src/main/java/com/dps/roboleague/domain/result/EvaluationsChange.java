package com.dps.roboleague.domain.result;

import com.dps.roboleague.domain.scoring.JudgeEvaluations;

/** Qué pasa con las evaluaciones de los jueces al corregir una corrida: se conservan o se reemplazan. */
public sealed interface EvaluationsChange permits KeepEvaluations, ReplaceEvaluations {

    static EvaluationsChange keep() {
        return new KeepEvaluations();
    }

    static EvaluationsChange replaceWith(JudgeEvaluations evaluations) {
        return new ReplaceEvaluations(evaluations);
    }

    /** Las evaluaciones que quedan vigentes a partir de las que la corrida tenía hasta ahora. */
    JudgeEvaluations over(JudgeEvaluations current);
}
