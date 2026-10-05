package com.dps.roboleague.domain.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.ranking.aggregation.BestAttempt;
import com.dps.roboleague.domain.ranking.aggregation.SumOfAttempts;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.ScoringRuleCode;
import com.dps.roboleague.domain.shared.ChallengeId;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RunId;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttemptAggregationTest {

    private final List<ScoredRun> attempts = List.of(run("R1", "30"), run("R2", "50"), run("R3", "-5"));

    @Test
    void bestAttemptKeepsOnlyTheHighestScoringAttempt() {
        assertEquals(Points.of("50"), new BestAttempt().aggregate(attempts));
    }

    @Test
    void sumOfAttemptsAddsUpEveryAttempt() {
        assertEquals(Points.of("75"), new SumOfAttempts().aggregate(attempts));
    }

    @Test
    void bestAttemptKeepsANegativeScoreWhenItIsTheOnlyOne() {
        assertEquals(Points.of("-5"), new BestAttempt().aggregate(List.of(run("R3", "-5"))));
    }

    @Test
    void aTeamWithoutAttemptsHasZeroPointsUnderAnyPolicy() {
        assertEquals(Points.ZERO, new BestAttempt().aggregate(List.of()));
        assertEquals(Points.ZERO, new SumOfAttempts().aggregate(List.of()));
    }

    @Test
    void eachPolicyDescribesItselfWithAStableCode() {
        assertEquals("BEST_ATTEMPT", new BestAttempt().code());
        assertEquals("SUM_OF_ATTEMPTS", new SumOfAttempts().code());
    }

    private ScoredRun run(String runId, String points) {
        return new ScoredRun(RunId.of(runId), ChallengeId.of("RESCUE"), MeasurementSet.empty(),
                new ScoreBreakdown(List.of(ScoreContribution.earned(ScoringRuleCode.of("CHALLENGE"), "base score",
                        Points.of(points)))));
    }
}