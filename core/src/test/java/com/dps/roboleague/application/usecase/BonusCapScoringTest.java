package com.dps.roboleague.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.port.in.AcceptAppeal;
import com.dps.roboleague.domain.port.in.CalculateRunScore;
import com.dps.roboleague.domain.port.in.GenerateStandings;
import com.dps.roboleague.domain.port.in.SubmitAppeal;
import com.dps.roboleague.domain.ranking.Revision;
import com.dps.roboleague.domain.ranking.Standings;
import com.dps.roboleague.domain.rulebook.RulebookVersion;
import com.dps.roboleague.domain.scoring.BonusCap;
import com.dps.roboleague.domain.scoring.ContributionKind;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import com.dps.roboleague.domain.scoring.ScoreContribution;
import com.dps.roboleague.domain.scoring.rule.ThresholdBonusRule;
import com.dps.roboleague.domain.shared.Actor;
import com.dps.roboleague.domain.shared.AppealId;
import com.dps.roboleague.domain.shared.Points;
import com.dps.roboleague.domain.shared.RoundId;
import com.dps.roboleague.domain.shared.RunId;
import com.dps.roboleague.domain.shared.TeamId;
import com.dps.roboleague.support.RescueEditionFixture;
import com.dps.roboleague.support.TestEdition;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BonusCapScoringTest {

    private final TestEdition edition = TestEdition.start();
    private final TeamId delta = edition.registerEligibleTeam("Delta Bots");

    @Test
    void theRunScoreExplainsEachBonusTheCapAndTheTrim() {
        publishThreeBonuses(Optional.of(BonusCap.of(25)));
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
    void eachRunIsScoredWithTheCapOfTheRulebookPinnedInItsRound() {
        RulebookVersion uncapped = publishThreeBonuses(Optional.empty());
        RunId earlier = captureAllBonuses(edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 1,
                List.of(delta)));
        RulebookVersion capped = publishThreeBonuses(Optional.of(BonusCap.of(25)));
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
        publishThreeBonuses(Optional.of(BonusCap.of(25)));
        RunId run = captureAllBonuses(edition.scheduleRoundFor(RescueEditionFixture.CHALLENGE_ID, 1,
                List.of(delta)));
        Standings first = generate();

        AppealId appeal = edition.module().submitAppealUseCase().execute(new SubmitAppeal.Command(run, delta,
                "the fifth objective was not completed", Actor.of("delta-captain")));
        edition.module().acceptAppealUseCase().execute(new AcceptAppeal.Command(appeal,
                "video review shows four objectives", edition.measurements("55", 4, "35"), List.of(),
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

    private RulebookVersion publishThreeBonuses(Optional<BonusCap> bonusCap) {
        return edition.publishRulebook(RescueEditionFixture.rulebook(RescueEditionFixture.challengeCappingBonuses(
                RescueEditionFixture.scoringRulesWithThreeBonuses(), bonusCap)));
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
