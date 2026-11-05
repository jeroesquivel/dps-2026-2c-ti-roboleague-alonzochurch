package com.dps.roboleague.domain.challenge;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dps.roboleague.domain.ranking.AllRounds;
import com.dps.roboleague.domain.ranking.RoundSelection;
import com.dps.roboleague.domain.scoring.BonusPolicy;
import com.dps.roboleague.domain.scoring.ScoreBreakdown;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Las variantes "no aplica" se comportan como política vacía, sin ramas en quien las usa. */
class NullObjectVariantsTest {

    @Test
    void anUncappedBonusPolicyNeitherTrimsNorReportsAnOutcome() {
        BonusPolicy uncapped = BonusPolicy.uncapped();
        ScoreBreakdown breakdown = new ScoreBreakdown(List.of());

        assertTrue(uncapped.trimContributions(breakdown).isEmpty());
        assertTrue(uncapped.capOutcomeOf(breakdown).isEmpty());
    }

    @Test
    void aSingleCaptureAcceptsSingleOperationsAndMeasuresEveryMetric() {
        CaptureMode single = CaptureMode.single();

        assertDoesNotThrow(() -> single.requireSingleCapture("Rescue mission"));
        assertDoesNotThrow(() -> single.requireRulesOfEverySource(List.of(), List.of(), "Rescue mission"));
        assertEquals(List.of(), single.measuredAmong(List.of()));
    }

    @Test
    void countingEveryRoundIsTheDefaultRoundSelection() {
        assertEquals(new AllRounds(), RoundSelection.all());
    }
}
