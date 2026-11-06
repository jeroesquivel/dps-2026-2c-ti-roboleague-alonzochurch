package com.dps.roboleague.adapter.in.web.run;

import com.dps.roboleague.rules.challenge.MetricKey;
import com.dps.roboleague.rules.scoring.JudgeEvaluation;
import com.dps.roboleague.rules.scoring.JudgeEvaluations;
import com.dps.roboleague.rules.scoring.JudgeScore;
import com.dps.roboleague.shared.InvalidValueException;
import com.dps.roboleague.shared.JudgeId;
import java.util.List;

/** Traduce las evaluaciones de los jueces de un pedido web; lo comparten la captura y las correcciones. */
public final class JudgeEvaluationsRequestMapper {

    private JudgeEvaluationsRequestMapper() {
    }

    public static JudgeEvaluations toDomain(List<EvaluationRequest> values) {
        return new JudgeEvaluations(required(values, "evaluations (an empty list if there are none)").stream()
                .map(value -> {
                    EvaluationRequest evaluation = required(value, "evaluation");
                    return new JudgeEvaluation(JudgeId.of(required(evaluation.judgeId(), "evaluation judgeId")),
                            MetricKey.of(required(evaluation.criterion(), "evaluation criterion")),
                            new JudgeScore(required(evaluation.score(), "evaluation score")));
                })
                .toList());
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new InvalidValueException(field + " is required");
        }
        return value;
    }
}
