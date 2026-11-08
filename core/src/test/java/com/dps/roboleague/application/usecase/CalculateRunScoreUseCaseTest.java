package com.dps.roboleague.application.usecase;

import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.rules.scoring.ScoreBreakdown;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import com.dps.roboleague.rules.scoring.rule.ObjectiveScoringRule;
import com.dps.roboleague.rules.scoring.rule.TimeScoringRule;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculateRunScoreUseCaseTest {

    private final TestEdition edition = TestEdition.start();
    private final TeamId delta = edition.registerEligibleTeam("Delta Bots");
    private final RoundId firstRound = edition.scheduleRoundFor(1, List.of(delta));

    @Test
    void explainsEveryContributionThatBuildsTheTotal() {
        RunId runId = edition.capture(firstRound, delta, "95.5", 4, "42", List.of(8, 9), List.of());

        CalculateRunScore.RunScore score = score(runId);
        ScoreBreakdown breakdown = score.breakdown();

        assertEquals(Points.of("60.75"), score.total());
        assertEquals(Points.of("12.25"), breakdown.totalFor(TimeScoringRule.CODE));
        assertEquals(Points.of("40.00"), breakdown.totalFor(ObjectiveScoringRule.CODE));
        assertEquals(score.total(), breakdown.contributions().stream()
                .map(ScoreContribution::points).reduce(Points.ZERO, Points::plus));
        assertTrue(breakdown.contributions().stream().noneMatch(contribution -> contribution.explanation().isBlank()));
    }

    @Test
    void identifiesTheRunItsTeamItsChallengeAndTheRulebookVersionPinnedInIt() {
        RunId runId = edition.capture(firstRound, delta, "95.5", 4, "42", List.of(8, 9), List.of());

        CalculateRunScore.RunScore score = score(runId);

        assertEquals(runId, score.runId());
        assertEquals(delta, score.teamId());
        assertEquals(RescueEditionFixture.CHALLENGE_ID, score.challengeId());
        assertEquals(RulebookVersion.first(), score.rulebookVersion());
        assertTrue(score.completion().isComplete());
    }

    private CalculateRunScore.RunScore score(RunId runId) {
        return edition.module().calculateRunScoreUseCase().execute(new CalculateRunScore.Command(runId));
    }
}
