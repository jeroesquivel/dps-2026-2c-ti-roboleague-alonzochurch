package com.dps.roboleague.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricKey;
import com.dps.roboleague.domain.scoring.rule.JudgePanelScoringRule;
import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.JudgeId;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RuleViolationException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JudgeEvaluationsTest {

    private static final MetricKey DESIGN = MetricKey.of("DESIGN");
    private static final MetricKey STYLE = MetricKey.of("STYLE");

    @Test
    void aJudgeEvaluatesEachCriterionOnlyOnceSoTheirScoreCannotWeighDouble() {
        InvalidValueException error = assertThrows(InvalidValueException.class,
                () -> JudgeEvaluations.of(evaluation("J1", DESIGN, 10), evaluation("J1", DESIGN, 10),
                        evaluation("J2", DESIGN, 0)));

        assertTrue(error.getMessage().contains("J1"));
    }

    @Test
    void theSameJudgeMayEvaluateDifferentCriteria() {
        JudgeEvaluations evaluations = JudgeEvaluations.of(evaluation("J1", DESIGN, 8), evaluation("J1", STYLE, 6),
                evaluation("J2", DESIGN, 6));

        assertEquals(2, evaluations.forCriterion(DESIGN).size());
        assertEquals(Set.of(DESIGN, STYLE), evaluations.criteria());
        assertEquals(Points.of(7), new ScoreBreakdown(new JudgePanelScoringRule(DESIGN, PointsRate.of(1))
                .apply(new ScoringContext(MeasurementSet.empty(), evaluations, List.of()))).total());
    }

    @Test
    void onlyTheJudgesOfThePanelMayEvaluate() {
        JudgeEvaluations evaluations = JudgeEvaluations.of(evaluation("J1", DESIGN, 8), evaluation("J3", DESIGN, 6));

        assertDoesNotThrow(() -> JudgeEvaluations.none().requireEvaluatorsWithin(Set.of(JudgeId.of("J1"))));
        assertThrows(RuleViolationException.class,
                () -> evaluations.requireEvaluatorsWithin(Set.of(JudgeId.of("J1"), JudgeId.of("J2"))));
    }

    private JudgeEvaluation evaluation(String judge, MetricKey criterion, long score) {
        return new JudgeEvaluation(JudgeId.of(judge), criterion, JudgeScore.of(score));
    }
}
