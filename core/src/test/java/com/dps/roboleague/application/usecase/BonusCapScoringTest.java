package com.dps.roboleague.application.usecase;

import com.dps.roboleague.appeals.port.in.AcceptAppeal;
import com.dps.roboleague.appeals.port.in.SubmitAppeal;
import com.dps.roboleague.results.port.in.CalculateRunScore;
import com.dps.roboleague.results.port.in.GenerateStandings;
import com.dps.roboleague.results.ranking.Revision;
import com.dps.roboleague.results.ranking.Standings;
import com.dps.roboleague.results.result.ResultAmendment;
import com.dps.roboleague.rules.scoring.BonusCap;
import com.dps.roboleague.rules.scoring.BonusCapOutcome;
import com.dps.roboleague.rules.scoring.BonusPolicy;
import com.dps.roboleague.rules.scoring.ContributionKind;
import com.dps.roboleague.rules.scoring.ScoreBreakdown;
import com.dps.roboleague.rules.scoring.ScoreContribution;
import com.dps.roboleague.rules.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.shared.Actor;
import com.dps.roboleague.shared.AppealId;
import com.dps.roboleague.shared.Points;
import com.dps.roboleague.shared.RoundId;
import com.dps.roboleague.shared.RulebookVersion;
import com.dps.roboleague.shared.RunId;
import com.dps.roboleague.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BonusCapScoringTest {

    private final TestEdition edition = TestEdition.start();
    private final TeamId delta = edition.registerEligibleTeam("Delta Bots");

    @Test
    void theRunScoreExplainsEachBonusTheCapAndTheTrim() {
        publishThreeBonuses(BonusCap.of(25));
        RunId run = captureAllBonuses(edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 1,
                List.of(delta)));

        ScoreBreakdown breakdown = score(run).breakdown();

        assertEquals(List.of(Points.of(15), Points.of(10), Points.of(10)), breakdown.contributions().stream()
                .filter(contribution -> contribution.ruleCode().equals(ThresholdBonusRule.CODE))
                .map(ScoreContribution::points)
                .toList());
        ScoreContribution trim = trimOf(breakdown).orElseThrow();
        assertEquals(Points.of(-10), trim.points());
        assertTrue(trim.explanation().contains("35.00"));
        assertTrue(trim.explanation().contains("25.00"));
        assertTrue(trim.explanation().contains("10.00"));
        assertEquals(Points.of(25), breakdown.totalOf(ContributionKind.BONUS));
        assertEquals(Points.of(75), score(run).total());
    }

    @Test
    void theRunScoreReportsTheBonusesObtainedTheCapAndTheTrimAsData() {
        RulebookVersion uncapped = publishThreeBonuses(BonusPolicy.uncapped());
        RunId earlier = captureAllBonuses(edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 1,
                List.of(delta)));
        publishThreeBonuses(BonusCap.of(25));
        RunId later = captureAllBonuses(edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 2,
                List.of(delta)));

        BonusCapOutcome outcome = score(later).bonusCap().orElseThrow();

        assertEquals(Points.of(35), outcome.obtained());
        assertEquals(Points.of(25), outcome.cap());
        assertEquals(Points.of(10), outcome.trimmed());
        assertEquals(Points.of(25), outcome.applied());
        assertEquals(uncapped, score(earlier).rulebookVersion());
        assertTrue(score(earlier).bonusCap().isEmpty());
    }

    @Test
    void eachRunIsScoredWithTheCapOfTheRulebookPinnedInItsRound() {
        RulebookVersion uncapped = publishThreeBonuses(BonusPolicy.uncapped());
        RunId earlier = captureAllBonuses(edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 1,
                List.of(delta)));
        RulebookVersion capped = publishThreeBonuses(BonusCap.of(25));
        RunId later = captureAllBonuses(edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 2,
                List.of(delta)));

        assertEquals(uncapped, score(earlier).rulebookVersion());
        assertTrue(trimOf(score(earlier).breakdown()).isEmpty());
        assertEquals(Points.of(85), score(earlier).total());
        assertEquals(capped, score(later).rulebookVersion());
        assertEquals(Points.of(-10), trimOf(score(later).breakdown()).orElseThrow().points());
        assertEquals(Points.of(75), score(later).total());
    }

    @Test
    void aCorrectionReappliesTheCapAndKeepsTheEarlierRevisionTrimmed() {
        publishThreeBonuses(BonusCap.of(25));
        RunId run = captureAllBonuses(edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 1,
                List.of(delta)));
        Standings first = generate();

        AppealId appeal = edition.module().submitAppealUseCase().execute(new SubmitAppeal.Command(run, delta,
                "the fifth objective was not completed", Actor.of("delta-captain")));
        edition.module().acceptAppealUseCase().execute(new AcceptAppeal.Command(appeal,
                "video review shows four objectives", ResultAmendment.of(edition.measurements("55", 4, "35"), List.of()),
                Actor.of("head-judge")));

        Standings recalculated = edition.latestStandings();
        ScoreContribution trim = trimOf(score(run).breakdown()).orElseThrow();
        assertEquals(Revision.of(2), recalculated.revision());
        assertEquals(Points.ZERO, trim.points());
        assertTrue(trim.explanation().contains("20.00"));
        assertEquals(Points.of(60), recalculated.entryFor(delta).orElseThrow().totalPoints());
        assertEquals(Points.of(75), first.entryFor(delta).orElseThrow().totalPoints());
        assertEquals(Points.of(75), edition.standingsHistory().getFirst().entryFor(delta).orElseThrow()
                .totalPoints());
    }

    private RulebookVersion publishThreeBonuses(BonusPolicy bonusPolicy) {
        return edition.publishRulebook(RescueEditionFixture.rulebook(RescueEditionFixture.challengeCappingBonuses(
                RescueEditionFixture.scoringRulesWithThreeBonuses(), bonusPolicy)));
    }

    private RunId captureAllBonuses(RoundId roundId) {
        return edition.capture(roundId, delta, "55", 5, "35", List.of(8, 9), List.of());
    }

    private CalculateRunScore.RunScore score(RunId runId) {
        return edition.module().calculateRunScoreUseCase().execute(new CalculateRunScore.Command(runId));
    }

    private Standings generate() {
        return edition.module().generateStandingsUseCase().execute(new GenerateStandings.Command(
                edition.competitionId(), edition.categoryId(), TestEdition.ACTOR));
    }

    private static Optional<ScoreContribution> trimOf(ScoreBreakdown breakdown) {
        return breakdown.contributions().stream()
                .filter(contribution -> contribution.ruleCode().equals(BonusCap.CODE))
                .findFirst();
    }
}
