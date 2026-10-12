package com.dps.roboleague.domain.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dps.roboleague.domain.challenge.ChallengeSpec;
import com.dps.roboleague.domain.challenge.MeasurementSet;
import com.dps.roboleague.domain.challenge.MetricValue;
import com.dps.roboleague.domain.ranking.aggregation.SumOfAttempts;
import com.dps.roboleague.domain.ranking.rule.FewestPenaltiesTiebreak;
import com.dps.roboleague.domain.scoring.BonusCap;
import com.dps.roboleague.domain.scoring.IncidentReport;
import com.dps.roboleague.domain.scoring.JudgeEvaluations;
import com.dps.roboleague.domain.scoring.ScoringContext;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BonusCapTiebreakTest {

    private final ChallengeSpec challenge = RescueEditionFixture.challengeCappingBonuses(
            RescueEditionFixture.scoringRulesWithThreeBonuses(), Optional.of(BonusCap.of(25)));

    @Test
    void theTrimDoesNotCountAsAPenaltyWhenBreakingATie() {
        TeamScoreSummary trimmed = summary("TRIMMED", measurements(5, "55", "35"));
        TeamScoreSummary untrimmed = summary("UNTRIMMED", measurements(5, "55", "45"));

        assertEquals(Points.of(72), trimmed.totalPoints());
        assertEquals(trimmed.totalPoints(), untrimmed.totalPoints());
        assertEquals(Points.of(-3), trimmed.penaltyPoints());
        assertEquals(trimmed.penaltyPoints(), untrimmed.penaltyPoints());
        assertEquals(0, new FewestPenaltiesTiebreak().compare(trimmed, untrimmed));
    }

    private TeamScoreSummary summary(String team, MeasurementSet measurements) {
        ScoringContext context = new ScoringContext(measurements, JudgeEvaluations.none(),
                List.of(IncidentReport.once(RescueEditionFixture.RESTART)));
        ScoredRun run = new ScoredRun(RunId.of(team + "-RUN"), RescueEditionFixture.CHALLENGE_ID, measurements,
                challenge.score(context));
        TeamRuns runs = new TeamRuns(List.of(run), new SumOfAttempts());
        return new TeamScoreSummary(TeamId.of(team), runs, new ScoreExplanation(List.of(runs.subtotal())));
    }

    private static MeasurementSet measurements(int objectives, String seconds, String energy) {
        return MeasurementSet.empty()
                .with(RescueEditionFixture.OBJECTIVES, MetricValue.of(objectives))
                .with(RescueEditionFixture.TIME, MetricValue.of(seconds))
                .with(RescueEditionFixture.ENERGY, MetricValue.of(energy));
    }
}
