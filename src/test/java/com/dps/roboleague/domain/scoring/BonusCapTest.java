package com.dps.roboleague.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dps.roboleague.domain.shared.InvalidValueException;
import com.dps.roboleague.domain.shared.Points;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BonusCapTest {

    private static final ScoringRuleCode RULE = ScoringRuleCode.of("RULE");

    @ParameterizedTest(name = "bonuses {0} with a cap of {1} keep {2}")
    @CsvSource({
            "35, 25, 25.00, -10.00",
            "25, 25, 25.00, 0.00",
            "10, 25, 10.00, 0.00",
            "15, 0, 0.00, -15.00",
            "0, 25, 0.00, 0.00"})
    void trimsTheSumOfTheBonusesDownToTheCap(long obtained, long cap, String applied, String trimContribution) {
        ScoreBreakdown breakdown = new ScoreBreakdown(List.of(ScoreContribution.bonus(RULE, "bonus",
                Points.of(obtained))));

        ScoreContribution trim = BonusCap.of(cap).trim(breakdown);

        assertEquals(BonusCap.CODE, trim.ruleCode());
        assertEquals(ContributionKind.BONUS, trim.kind());
        assertEquals(Points.of(trimContribution), trim.points());
        assertEquals(Points.of(applied), Points.of(obtained).plus(trim.points()));
    }

    @Test
    void comparesTheCapAgainstTheSumAndNotAgainstEachBonus() {
        ScoreBreakdown breakdown = new ScoreBreakdown(List.of(
                ScoreContribution.bonus(RULE, "first", Points.of(15)),
                ScoreContribution.bonus(RULE, "second", Points.of(10)),
                ScoreContribution.bonus(RULE, "third", Points.of(10))));

        assertEquals(Points.of(-10), BonusCap.of(25).trim(breakdown).points());
    }

    @Test
    void ignoresEarnedPointsAndPenalties() {
        ScoreBreakdown breakdown = new ScoreBreakdown(List.of(
                ScoreContribution.earned(RULE, "earned", Points.of(50)),
                ScoreContribution.bonus(RULE, "bonus", Points.of(20)),
                ScoreContribution.penalty(RULE, "penalty", Points.of(-30))));

        assertEquals(Points.ZERO, BonusCap.of(25).trim(breakdown).points());
    }

    @Test
    void explainsTheBonusesObtainedTheCapAndTheTrim() {
        ScoreBreakdown breakdown = new ScoreBreakdown(List.of(ScoreContribution.bonus(RULE, "bonus",
                Points.of(35))));

        assertEquals("bonuses obtained 35.00 exceed the cap of 25.00: 10.00 trimmed",
                BonusCap.of(25).trim(breakdown).explanation());
    }

    @Test
    void explainsThatNothingIsTrimmedWithinTheCap() {
        ScoreBreakdown breakdown = new ScoreBreakdown(List.of(ScoreContribution.bonus(RULE, "bonus",
                Points.of(25))));

        assertEquals("bonuses obtained 25.00 are within the cap of 25.00: nothing trimmed",
                BonusCap.of(25).trim(breakdown).explanation());
    }

    @Test
    void rejectsANegativeOrMissingMaximum() {
        assertThrows(InvalidValueException.class, () -> BonusCap.of(-1));
        assertThrows(NullPointerException.class, () -> new BonusCap(null));
    }
}
